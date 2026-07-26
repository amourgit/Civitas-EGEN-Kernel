package africa.civitas.egen.kernel.eventbus.kafka;

import africa.civitas.egen.kernel.eventbus.api.Abonnement;
import africa.civitas.egen.kernel.eventbus.api.EventBus;
import africa.civitas.egen.kernel.eventbus.api.EventHandler;
import africa.civitas.egen.kernel.eventbus.api.EventPublishException;
import africa.civitas.egen.kernel.sdk.event.EventEnvelope;
import africa.civitas.egen.kernel.sdk.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation livree d'{@link EventBus} adossee a Kafka — le choix technologique
 * acte pour EGEN (anatomie du Kernel, §4 : retention/relecture longue duree
 * privilegiees sur la latence, pour un systeme vise a 10-20 ans avec des modules
 * ajoutes au fil du temps).
 *
 * <p><b>Convention de topic</b> : un topic par systeme d'origine (premier segment
 * d'un {@link EventType}, prefixe {@code egen.}) — {@code organisation.affectation.
 * terminee} et {@code organisation.tutelle.etablie} partagent le topic {@code
 * egen.organisation}. Ce choix, plus grossier qu'un topic par type exact, evite une
 * proliferation de topics et rend la souscription par prefixe (deja prevue par
 * {@link EventType#systemeOrigine()}) directe : un seul abonnement Kafka par systeme
 * d'origine ecoute, quel que soit le nombre de types precis qu'il porte.
 *
 * <p><b>Cle de partition</b> : {@code contexteId}, pour que tous les evenements d'un
 * meme Contexte (Organisation ou Cellule) atterrissent sur la meme partition — un
 * ordre de livraison preserve par Contexte, jamais garanti globalement (ce que Kafka
 * ne garantit d'ailleurs jamais au-dela d'une partition).
 *
 * <p><b>Limite assumee et documentee sur la charge utile</b> : la charge utile
 * generique ({@code T payload}) traverse Kafka comme une structure JSON, jamais
 * comme le type Java d'origine — {@link EnvelopeJson} en explique la raison. Un
 * gestionnaire recevra typiquement un {@code java.util.Map} pour une charge utile
 * structuree, pas l'instance de la classe Java d'origine. C'est une limite reelle de
 * cette premiere livraison, pas un defaut cache : un mecanisme d'enregistrement avec
 * classe cible explicite pourrait lever cette limite dans une iteration future, sans
 * que le contrat {@link EventBus} lui-meme n'ait besoin de changer.
 *
 * <p><b>Portee des tests</b> : comme {@code Pf4jPluginLoader} dans
 * kernel-plugin-engine, cette classe n'est pas couverte par un test d'integration
 * reel dans ce depot (aucun courtier Kafka disponible dans ce sandbox). Toute la
 * logique de dispatch (correspondance type exact/prefixe, isolation des
 * gestionnaires en echec) est neanmoins identique, ligne pour ligne dans son
 * intention, a celle d'{@code InMemoryEventBus}, qui elle est entierement testee —
 * seule la frontiere serialisation/reseau change.
 */
public final class KafkaEventBusAdapter implements EventBus, AutoCloseable {

    private static final String PREFIXE_TOPIC = "egen.";
    private static final Duration DELAI_SCRUTATION = Duration.ofMillis(500);
    private static final Duration DELAI_ARRET = Duration.ofSeconds(5);

    private record SouscriptionInterne(
            Abonnement abonnement, EventType typeExact, String prefixe, EventHandler<Object> gestionnaire) {
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final Map<UUID, SouscriptionInterne> souscriptions = new ConcurrentHashMap<>();
    private final Thread threadConsommation;

    private volatile Set<String> topicsSouhaites = Set.of();
    private volatile boolean actif = true;

    public KafkaEventBusAdapter(String bootstrapServers, String consumerGroupId) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers ne peut pas etre nul.");
        Objects.requireNonNull(consumerGroupId, "consumerGroupId ne peut pas etre nul.");

        Properties proprietesProducteur = new Properties();
        proprietesProducteur.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        proprietesProducteur.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        proprietesProducteur.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(proprietesProducteur);

        Properties proprietesConsommateur = new Properties();
        proprietesConsommateur.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        proprietesConsommateur.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        proprietesConsommateur.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        proprietesConsommateur.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        proprietesConsommateur.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        this.consumer = new KafkaConsumer<>(proprietesConsommateur);

        this.threadConsommation = new Thread(this::boucleDeConsommation, "egen-eventbus-kafka-consumer");
        this.threadConsommation.setDaemon(true);
        this.threadConsommation.start();
    }

    @Override
    public void publier(EventEnvelope<?> evenement) {
        if (evenement == null) {
            throw new IllegalArgumentException("evenement ne peut pas etre nul.");
        }
        String topic = topicPour(evenement.type());
        EnvelopeJson enveloppeJson = new EnvelopeJson(
                evenement.eventId(), evenement.type().name(), evenement.contexteId(),
                evenement.occurredAt(), evenement.payload());

        String json;
        try {
            json = mapper.writeValueAsString(enveloppeJson);
        } catch (Exception e) {
            throw new EventPublishException(
                    "Impossible de serialiser l'evenement " + evenement.type().name() + " : " + e.getMessage(), e);
        }

        try {
            producer.send(new ProducerRecord<>(topic, evenement.contexteId().toString(), json)).get();
        } catch (Exception e) {
            throw new EventPublishException(
                    "Echec de la publication de l'evenement " + evenement.type().name()
                            + " sur le topic '" + topic + "' : " + e.getMessage(), e);
        }
    }

    @Override
    public Abonnement souscrire(String moduleId, EventType type, EventHandler<?> gestionnaire) {
        Objects.requireNonNull(type, "type ne peut pas etre nul.");
        Objects.requireNonNull(gestionnaire, "gestionnaire ne peut pas etre nul.");
        Abonnement abonnement = new Abonnement(UUID.randomUUID(), moduleId, "type exact : " + type.name());
        enregistrer(new SouscriptionInterne(abonnement, type, null, castGestionnaire(gestionnaire)));
        return abonnement;
    }

    @Override
    public Abonnement souscrireParPrefixe(String moduleId, String prefixeSystemeOrigine, EventHandler<?> gestionnaire) {
        if (prefixeSystemeOrigine == null || prefixeSystemeOrigine.isBlank()) {
            throw new IllegalArgumentException("prefixeSystemeOrigine ne peut pas etre vide.");
        }
        Objects.requireNonNull(gestionnaire, "gestionnaire ne peut pas etre nul.");
        Abonnement abonnement = new Abonnement(
                UUID.randomUUID(), moduleId, "prefixe : " + prefixeSystemeOrigine + ".*");
        enregistrer(new SouscriptionInterne(abonnement, null, prefixeSystemeOrigine, castGestionnaire(gestionnaire)));
        return abonnement;
    }

    @Override
    public void desabonner(Abonnement abonnement) {
        if (abonnement == null) {
            throw new IllegalArgumentException("abonnement ne peut pas etre nul.");
        }
        souscriptions.remove(abonnement.id());
        recalculerTopicsSouhaites();
    }

    @Override
    public int desabonnerToutPour(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        int[] compteur = {0};
        souscriptions.entrySet().removeIf(entree -> {
            boolean estDuModule = entree.getValue().abonnement().moduleId().equals(moduleId);
            if (estDuModule) {
                compteur[0]++;
            }
            return estDuModule;
        });
        recalculerTopicsSouhaites();
        return compteur[0];
    }

    /**
     * Ferme proprement le producteur et la consommation en tache de fond. Attend au
     * plus {@link #DELAI_ARRET} l'arret effectif du thread de consommation avant de
     * continuer, sans jamais bloquer indefiniment.
     */
    @Override
    public void close() {
        actif = false;
        consumer.wakeup();
        try {
            threadConsommation.join(DELAI_ARRET.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        producer.close();
    }

    private void enregistrer(SouscriptionInterne souscription) {
        souscriptions.put(souscription.abonnement().id(), souscription);
        recalculerTopicsSouhaites();
    }

    private void recalculerTopicsSouhaites() {
        topicsSouhaites = souscriptions.values().stream()
                .map(s -> s.typeExact() != null ? topicPour(s.typeExact()) : PREFIXE_TOPIC + s.prefixe())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String topicPour(EventType type) {
        return PREFIXE_TOPIC + type.systemeOrigine();
    }

    /**
     * Boucle unique de consommation : relit et applique {@link #topicsSouhaites} a
     * chaque iteration AVANT de scruter, jamais depuis un autre thread — {@code
     * KafkaConsumer} n'est pas thread-safe, {@code subscribe} et {@code poll}
     * doivent toujours provenir du meme thread. C'est precisement pour respecter
     * cette contrainte que les methodes de souscription/desabonnement ne font que
     * mettre a jour un ensemble partage ({@code volatile}), jamais toucher {@code
     * consumer} directement.
     */
    private void boucleDeConsommation() {
        Set<String> topicsActuellementSouscrits = Set.of();
        try {
            while (actif) {
                Set<String> voulus = topicsSouhaites;
                if (!topicsActuellementSouscrits.equals(voulus)) {
                    if (voulus.isEmpty()) {
                        consumer.unsubscribe();
                    } else {
                        consumer.subscribe(voulus);
                    }
                    topicsActuellementSouscrits = voulus;
                }

                if (topicsActuellementSouscrits.isEmpty()) {
                    dormir(DELAI_SCRUTATION);
                    continue;
                }

                ConsumerRecords<String, String> messages = consumer.poll(DELAI_SCRUTATION);
                for (ConsumerRecord<String, String> message : messages) {
                    distribuer(message.value());
                }
            }
        } catch (WakeupException e) {
            // Signal d'arret volontaire (voir close()) — sortie normale de boucle.
        } finally {
            consumer.close();
        }
    }

    private void distribuer(String json) {
        EventType type;
        EventEnvelope<Object> enveloppe;
        try {
            EnvelopeJson enveloppeJson = mapper.readValue(json, EnvelopeJson.class);
            type = new EventType(enveloppeJson.type);
            enveloppe = new EventEnvelope<>(
                    enveloppeJson.eventId, type, enveloppeJson.contexteId,
                    enveloppeJson.occurredAt, enveloppeJson.payload);
        } catch (RuntimeException | JsonProcessingException e) {
            System.err.println("KafkaEventBusAdapter : message illisible ou invalide, ignore : " + e.getMessage());
            return;
        }

        for (SouscriptionInterne souscription : souscriptions.values()) {
            boolean correspond = type.equals(souscription.typeExact())
                    || (souscription.prefixe() != null
                            && type.systemeOrigine().equals(souscription.prefixe()));
            if (!correspond) {
                continue;
            }
            try {
                souscription.gestionnaire().traiter(enveloppe);
            } catch (RuntimeException e) {
                System.err.println("KafkaEventBusAdapter : le gestionnaire de l'abonnement "
                        + souscription.abonnement().id() + " (module '" + souscription.abonnement().moduleId()
                        + "') a leve une exception en traitant " + type.name() + " : " + e.getMessage());
            }
        }
    }

    private static void dormir(Duration duree) {
        try {
            Thread.sleep(duree.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private static EventHandler<Object> castGestionnaire(EventHandler<?> gestionnaire) {
        return (EventHandler<Object>) gestionnaire;
    }
}
