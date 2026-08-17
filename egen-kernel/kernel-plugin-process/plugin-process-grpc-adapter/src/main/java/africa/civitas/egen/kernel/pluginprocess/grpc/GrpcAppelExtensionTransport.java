package africa.civitas.egen.kernel.pluginprocess.grpc;

import africa.civitas.egen.kernel.pluginprocess.AppelExtensionTransport;
import africa.civitas.egen.kernel.pluginprocess.PluginProcessException;
import africa.civitas.egen.kernel.pluginprocess.PluginProcessHandshake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * L'implementation reelle de {@link AppelExtensionTransport} : chaque appel de
 * methode relaye par {@code PontExtensionDistante} (plugin-process-api) devient un
 * appel gRPC vers le processus plugin, authentifie par le mTLS ephemere etabli au
 * lancement (voir {@link MaterielTlsEphemere}).
 *
 * <p>Une instance par processus plugin — jamais partagee entre deux processus, le
 * canal gRPC qu'elle enveloppe etant lie a un couple (port, certificat) precis. Doit
 * toujours etre fermee ({@link #close()}) quand le processus plugin correspondant
 * est arrete, jamais laissee ouverte apres coup.
 */
public final class GrpcAppelExtensionTransport implements AppelExtensionTransport, AutoCloseable {

    private final ManagedChannel canal;
    private final ServiceExtensionDistanteGrpc.ServiceExtensionDistanteBlockingStub souche;
    private final ObjectMapper objectMapper;
    private final Duration delaiAppel;

    private GrpcAppelExtensionTransport(ManagedChannel canal, ObjectMapper objectMapper, Duration delaiAppel) {
        this.canal = canal;
        this.souche = ServiceExtensionDistanteGrpc.newBlockingStub(canal);
        this.objectMapper = objectMapper;
        this.delaiAppel = delaiAppel;
    }

    /**
     * Etablit la connexion mTLS vers un processus plugin deja lance et dont le
     * handshake a deja ete recu — jamais avant.
     *
     * @param hote l'hote sur lequel le processus plugin ecoute — toujours local en
     *             pratique (le processus plugin tourne sur la meme machine que
     *             l'hote), mais jamais suppose par cette classe
     * @param handshake le handshake recu du processus plugin — porte son port et
     *                   son certificat complet
     * @param materielHote le materiel TLS de l'hote lui-meme, deja transmis au
     *                      processus plugin via son environnement au lancement (voir
     *                      {@code PluginProcessLauncher#lancer}) — c'est ce meme
     *                      materiel qui doit servir ici, jamais un autre genere a la
     *                      volee, sous peine que le plugin rejette la connexion
     *                      (il n'a ete configure a faire confiance qu'a celui-la)
     * @throws PluginProcessException si les identifiants mTLS ne peuvent pas etre
     *                                  construits a partir de ce materiel
     */
    public static GrpcAppelExtensionTransport connecter(
            String hote,
            PluginProcessHandshake handshake,
            MaterielTlsEphemere materielHote,
            ObjectMapper objectMapper,
            Duration delaiAppel) {
        Objects.requireNonNull(hote, "hote ne peut pas etre nul.");
        Objects.requireNonNull(handshake, "handshake ne peut pas etre nul.");
        Objects.requireNonNull(materielHote, "materielHote ne peut pas etre nul.");
        Objects.requireNonNull(objectMapper, "objectMapper ne peut pas etre nul.");
        if (delaiAppel == null || delaiAppel.isNegative() || delaiAppel.isZero()) {
            throw new IllegalArgumentException("delaiAppel doit etre strictement positif.");
        }

        ChannelCredentials credentials = construireCredentials(handshake, materielHote);
        ManagedChannel canal = Grpc.newChannelBuilderForAddress(hote, handshake.port(), credentials).build();
        return new GrpcAppelExtensionTransport(canal, objectMapper, delaiAppel);
    }

    private static ChannelCredentials construireCredentials(
            PluginProcessHandshake handshake, MaterielTlsEphemere materielHote) {
        try (InputStream certificatHote = versFlux(materielHote.certificatPem());
             InputStream clePriveeHote = versFlux(materielHote.clePriveePem());
             InputStream certificatPlugin = new ByteArrayInputStream(handshake.certificatServeurDer())) {
            return TlsChannelCredentials.newBuilder()
                    .keyManager(certificatHote, clePriveeHote)
                    .trustManager(certificatPlugin)
                    .build();
        } catch (IOException e) {
            throw new PluginProcessException(
                    "Echec de construction des identifiants mTLS vers le processus plugin : " + e.getMessage(), e);
        }
    }

    private static InputStream versFlux(String pem) {
        return new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public Object invoquer(Class<?> pointExtension, Method methode, Object[] arguments) throws Exception {
        RequeteInvocation requete = construireRequete(pointExtension, methode, arguments);

        ReponseInvocation reponse;
        try {
            reponse = souche
                    .withDeadlineAfter(delaiAppel.toMillis(), TimeUnit.MILLISECONDS)
                    .invoquer(requete);
        } catch (StatusRuntimeException e) {
            throw new PluginProcessException(
                    "Echec de l'appel gRPC '" + methode.getName() + "' vers le processus plugin ("
                            + e.getStatus() + ").", e);
        }

        if (reponse.hasEchec()) {
            EchecInvocation echec = reponse.getEchec();
            throw new PluginProcessException(
                    "L'extension distante a leve " + echec.getTypeExceptionDistant()
                            + " : " + echec.getMessageDistant());
        }

        if (methode.getReturnType() == void.class) {
            return null;
        }
        return objectMapper.readValue(
                reponse.getValeurRetourJson(),
                objectMapper.getTypeFactory().constructType(methode.getGenericReturnType()));
    }

    private RequeteInvocation construireRequete(Class<?> pointExtension, Method methode, Object[] arguments)
            throws JsonProcessingException {
        Class<?>[] typesDeclares = methode.getParameterTypes();
        List<String> typesParametres = new ArrayList<>(arguments.length);
        List<String> argumentsJson = new ArrayList<>(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            typesParametres.add(typesDeclares[i].getName());
            argumentsJson.add(objectMapper.writeValueAsString(arguments[i]));
        }

        return RequeteInvocation.newBuilder()
                .setPointExtension(pointExtension.getName())
                .setMethode(methode.getName())
                .addAllTypesParametres(typesParametres)
                .addAllArgumentsJson(argumentsJson)
                .build();
    }

    /** Ferme le canal gRPC sous-jacent — attend un arret propre avant de forcer, jamais une fuite de ressources. */
    @Override
    public void close() {
        canal.shutdown();
        try {
            if (!canal.awaitTermination(5, TimeUnit.SECONDS)) {
                canal.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            canal.shutdownNow();
        }
    }
}
