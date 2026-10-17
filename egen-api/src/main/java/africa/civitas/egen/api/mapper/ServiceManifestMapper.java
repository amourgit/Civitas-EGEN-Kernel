package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ConditionDto;
import africa.civitas.egen.api.dto.ConfigurationDto;
import africa.civitas.egen.api.dto.DependenciesDto;
import africa.civitas.egen.api.dto.DeploymentDto;
import africa.civitas.egen.api.dto.EventsDto;
import africa.civitas.egen.api.dto.HealthDto;
import africa.civitas.egen.api.dto.HttpCheckDto;
import africa.civitas.egen.api.dto.LifecycleDto;
import africa.civitas.egen.api.dto.MetadataDto;
import africa.civitas.egen.api.dto.ReplicasDto;
import africa.civitas.egen.api.dto.ResourceDto;
import africa.civitas.egen.api.dto.ResourcesDto;
import africa.civitas.egen.api.dto.RuntimeDto;
import africa.civitas.egen.api.dto.ServiceDependencyDto;
import africa.civitas.egen.api.dto.ServiceInterfaceDto;
import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.api.dto.ServiceStatusDto;
import africa.civitas.egen.api.dto.ShutdownDto;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.ConfigurationSpec;
import africa.civitas.egen.domain.model.Dependency;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ManifestMetadata;
import africa.civitas.egen.domain.model.OwnerTeam;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.ResourceReference;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceInterface;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO &lt;-&gt; Domain — aucune fuite de type de domaine dans les DTO, ni
 * l'inverse (voir docs/architecture/16-packages-et-stack-technique.md,
 * "egen-api/mapper"). Chaque invariant reste applique par le domaine
 * lui-meme (constructeurs des Value Objects) : ce mapper ne fait que
 * traduire la forme, jamais la validation.
 */
public final class ServiceManifestMapper {

    private ServiceManifestMapper() {
    }

    public static ServiceManifest toDomain(ServiceManifestDto dto) {
        if (dto.metadata == null) {
            throw new IllegalArgumentException("ServiceManifest.metadata est obligatoire");
        }
        if (dto.runtime == null) {
            throw new IllegalArgumentException("ServiceManifest.runtime est obligatoire");
        }
        if (dto.deployment == null || dto.deployment.resources == null || dto.deployment.replicas == null) {
            throw new IllegalArgumentException("ServiceManifest.deployment est obligatoire");
        }

        ServiceId id = ServiceId.of(dto.metadata.name);
        ServiceVersion version = ServiceVersion.parse(dto.metadata.version);
        // runtime.artifact est optionnel (docs/architecture/06 : l'exemple annote ne le porte pas) ;
        // absent, c'est deployment.image — ce que le Deployment Adapter deploie reellement.
        String artifact = dto.runtime.artifact == null || dto.runtime.artifact.isBlank()
                ? dto.deployment.image : dto.runtime.artifact;
        ServiceRuntime runtime = new ServiceRuntime(
                RuntimeType.valueOf(dto.runtime.type.toUpperCase()),
                artifact,
                dto.runtime.language);
        DeploymentSpec deployment = new DeploymentSpec(
                dto.deployment.adapter,
                dto.deployment.image,
                dto.deployment.resources.cpu,
                dto.deployment.resources.memory,
                new ReplicaRange(dto.deployment.replicas.min, dto.deployment.replicas.max));

        if (dto.health == null || dto.health.http == null) {
            throw new IllegalArgumentException("ServiceManifest.health.http est obligatoire");
        }
        HealthSpec health = new HealthSpec(
                dto.health.http.endpoint,
                parseSecondsSuffix(dto.health.http.interval, "health.http.interval"),
                parseSecondsSuffix(dto.health.http.timeout, "health.http.timeout"),
                dto.health.http.failuresBeforeUnhealthy);

        LifecyclePolicy lifecycle = null;
        if (dto.lifecycle != null && dto.lifecycle.shutdown != null
                && dto.lifecycle.shutdown.gracePeriod != null) {
            lifecycle = new LifecyclePolicy(
                    parseSecondsSuffix(dto.lifecycle.shutdown.gracePeriod, "lifecycle.shutdown.gracePeriod"));
        }

        List<Dependency> dependencies = new ArrayList<>();
        if (dto.dependencies != null && dto.dependencies.services != null) {
            for (var serviceDependencyDto : dto.dependencies.services) {
                dependencies.add(new Dependency(
                        ServiceId.of(serviceDependencyDto.name),
                        serviceDependencyDto.versionConstraint,
                        serviceDependencyDto.required));
            }
        }

        // team est optionnel en lecture (retro-compatibilite, voir
        // docs/architecture/13-api-et-contrats.md) : un manifeste qui ne le
        // declare pas obtient OwnerTeam.unassigned() (voir OwnerTeam) plutot
        // que de faire echouer le Declare — le RBAC cote egen-api (voir
        // docs/architecture/14-securite.md) decide alors explicitement quoi
        // faire d'un service sans equipe assignee.
        OwnerTeam ownerTeam = (dto.metadata.team == null || dto.metadata.team.isBlank())
                ? null
                : OwnerTeam.of(dto.metadata.team);

        return new ServiceManifest(id, version, runtime, deployment, health, lifecycle, dependencies, ownerTeam,
                ResiliencyMapper.toDomain(dto.resiliency), toEvents(dto.events), toConfiguration(dto.configuration),
                toInterfaces(dto.interfaces), toResources(dto.dependencies),
                new ManifestMetadata(dto.metadata.labels, dto.metadata.annotations));
    }

    // ------------------------------------------------------------------ domaine -> DTO

    /**
     * Forme lisible du manifeste, relisible par {@link #toDomain} : memes champs que la declaration, valeurs
     * EFFECTIVES (defauts inclus). Un bloc vide est omis, pour qu'un bloc absent a la declaration le reste a la lecture.
     */
    public static ServiceManifestDto toDto(ServiceManifest manifest) {
        ServiceManifestDto dto = new ServiceManifestDto();
        dto.apiVersion = "egen.civitas.africa/v1";
        dto.kind = "ServiceManifest";

        dto.metadata = new MetadataDto();
        dto.metadata.name = manifest.id().value();
        dto.metadata.version = manifest.version().toString();
        dto.metadata.team = manifest.ownerTeam().isUnassigned() ? null : manifest.ownerTeam().value();
        dto.metadata.labels = manifest.metadata().labels().isEmpty() ? null : manifest.metadata().labels();
        dto.metadata.annotations = manifest.metadata().annotations().isEmpty() ? null : manifest.metadata().annotations();

        dto.runtime = new RuntimeDto();
        dto.runtime.type = manifest.runtime().type().name().toLowerCase(java.util.Locale.ROOT);
        dto.runtime.artifact = manifest.runtime().artifact();
        dto.runtime.language = manifest.runtime().language();

        dto.deployment = new DeploymentDto();
        dto.deployment.adapter = manifest.deployment().adapter();
        dto.deployment.image = manifest.deployment().image();
        dto.deployment.resources = new ResourcesDto();
        dto.deployment.resources.cpu = manifest.deployment().cpu();
        dto.deployment.resources.memory = manifest.deployment().memory();
        dto.deployment.replicas = new ReplicasDto();
        dto.deployment.replicas.min = manifest.deployment().replicas().min();
        dto.deployment.replicas.max = manifest.deployment().replicas().max();

        dto.health = new HealthDto();
        dto.health.http = new HttpCheckDto();
        dto.health.http.endpoint = manifest.health().httpEndpoint();
        dto.health.http.interval = manifest.health().interval().toSeconds() + "s";
        dto.health.http.timeout = manifest.health().timeout().toSeconds() + "s";
        dto.health.http.failuresBeforeUnhealthy = manifest.health().failuresBeforeUnhealthy();

        dto.lifecycle = new LifecycleDto();
        dto.lifecycle.shutdown = new ShutdownDto();
        dto.lifecycle.shutdown.gracePeriod = manifest.lifecycle().shutdownGracePeriod().toSeconds() + "s";

        if (!manifest.dependencies().isEmpty() || !manifest.resources().isEmpty()) {
            dto.dependencies = new DependenciesDto();
            if (!manifest.dependencies().isEmpty()) {
                dto.dependencies.services = new ArrayList<>();
                for (Dependency dependency : manifest.dependencies()) {
                    ServiceDependencyDto item = new ServiceDependencyDto();
                    item.name = dependency.serviceId().value();
                    item.versionConstraint = dependency.versionConstraint();
                    item.required = dependency.required();
                    dto.dependencies.services.add(item);
                }
            }
            if (!manifest.resources().isEmpty()) {
                dto.dependencies.resources = new ArrayList<>();
                for (ResourceReference resource : manifest.resources()) {
                    ResourceDto item = new ResourceDto();
                    item.name = resource.name();
                    item.type = resource.type();
                    item.params = resource.params().isEmpty() ? null : resource.params();
                    dto.dependencies.resources.add(item);
                }
            }
        }

        dto.resiliency = ResiliencyMapper.toDto(manifest.resiliency());

        if (!manifest.events().allTypes().isEmpty()) {
            dto.events = new EventsDto();
            dto.events.publishes = new ArrayList<>();
            for (EventSpec.Published published : manifest.events().publishes()) {
                EventsDto.PublishedEventDto item = new EventsDto.PublishedEventDto();
                item.type = published.type();
                item.schemaRef = published.schemaRef();
                dto.events.publishes.add(item);
            }
            dto.events.consumes = new ArrayList<>();
            for (EventSpec.Consumed consumed : manifest.events().consumes()) {
                EventsDto.ConsumedEventDto item = new EventsDto.ConsumedEventDto();
                item.type = consumed.type();
                item.subscriptionGroup = consumed.subscriptionGroup();
                if (consumed.deadLetter() != null) {
                    item.deadLetter = new EventsDto.DeadLetterDto();
                    item.deadLetter.maxRetries = consumed.deadLetter().maxRetries();
                    item.deadLetter.strategy = consumed.deadLetter().strategy();
                }
                dto.events.consumes.add(item);
            }
        }

        if (!manifest.configuration().isEmpty()) {
            dto.configuration = new ConfigurationDto();
            dto.configuration.required = new ArrayList<>();
            for (ConfigurationSpec.Key key : manifest.configuration().required()) {
                ConfigurationDto.KeyDto item = new ConfigurationDto.KeyDto();
                item.key = key.key();
                item.type = key.type();
                item.defaultValue = key.defaultValue();
                dto.configuration.required.add(item);
            }
            dto.configuration.secretsRefs = new ArrayList<>();
            for (ConfigurationSpec.SecretReferenceSpec ref : manifest.configuration().secretsRefs()) {
                ConfigurationDto.SecretRefDto item = new ConfigurationDto.SecretRefDto();
                item.key = ref.key();
                item.provider = ref.provider();
                item.path = ref.path();
                dto.configuration.secretsRefs.add(item);
            }
        }

        if (!manifest.interfaces().isEmpty()) {
            dto.interfaces = new ArrayList<>();
            for (ServiceInterface serviceInterface : manifest.interfaces()) {
                ServiceInterfaceDto item = new ServiceInterfaceDto();
                item.type = serviceInterface.type();
                item.ref = serviceInterface.ref();
                item.riskDefault = serviceInterface.riskDefault();
                dto.interfaces.add(item);
            }
        }
        return dto;
    }

    private static List<ResourceReference> toResources(DependenciesDto dto) {
        if (dto == null || dto.resources == null) {
            return null;
        }
        List<ResourceReference> resources = new ArrayList<>();
        for (ResourceDto item : dto.resources) {
            requireEntry(item, "dependencies.resources");
            resources.add(new ResourceReference(item.name, item.type, item.params));
        }
        return resources;
    }

    private static EventSpec toEvents(EventsDto dto) {
        if (dto == null) {
            return null;
        }
        List<EventSpec.Published> publishes = new ArrayList<>();
        if (dto.publishes != null) {
            for (EventsDto.PublishedEventDto item : dto.publishes) {
                requireEntry(item, "events.publishes");
                publishes.add(new EventSpec.Published(item.type, item.schemaRef));
            }
        }
        List<EventSpec.Consumed> consumes = new ArrayList<>();
        if (dto.consumes != null) {
            for (EventsDto.ConsumedEventDto item : dto.consumes) {
                requireEntry(item, "events.consumes");
                EventSpec.DeadLetter deadLetter = item.deadLetter == null ? null : new EventSpec.DeadLetter(
                        item.deadLetter.maxRetries == null ? 0 : item.deadLetter.maxRetries, item.deadLetter.strategy);
                consumes.add(new EventSpec.Consumed(item.type, item.subscriptionGroup, deadLetter));
            }
        }
        return new EventSpec(publishes, consumes);
    }

    private static ConfigurationSpec toConfiguration(ConfigurationDto dto) {
        if (dto == null) {
            return null;
        }
        List<ConfigurationSpec.Key> required = new ArrayList<>();
        if (dto.required != null) {
            for (ConfigurationDto.KeyDto item : dto.required) {
                requireEntry(item, "configuration.required");
                required.add(new ConfigurationSpec.Key(item.key, item.type, item.defaultValue));
            }
        }
        List<ConfigurationSpec.SecretReferenceSpec> secretsRefs = new ArrayList<>();
        if (dto.secretsRefs != null) {
            for (ConfigurationDto.SecretRefDto item : dto.secretsRefs) {
                requireEntry(item, "configuration.secretsRefs");
                secretsRefs.add(new ConfigurationSpec.SecretReferenceSpec(item.key, item.provider, item.path));
            }
        }
        return new ConfigurationSpec(required, secretsRefs);
    }

    private static List<ServiceInterface> toInterfaces(List<ServiceInterfaceDto> dtos) {
        if (dtos == null) {
            return null;
        }
        List<ServiceInterface> interfaces = new ArrayList<>();
        for (ServiceInterfaceDto item : dtos) {
            requireEntry(item, "interfaces");
            interfaces.add(new ServiceInterface(item.type, item.ref, item.riskDefault));
        }
        return interfaces;
    }

    /** Une entree de liste YAML vide (« - ») deserialise en null : message explicite plutot qu'un NPE. */
    private static void requireEntry(Object entry, String field) {
        if (entry == null) {
            throw new IllegalArgumentException(field + " : entree vide");
        }
    }

    /** L'equipe declaree dans un manifeste DTO, avant meme sa validation complete par {@link #toDomain}. */
    public static String teamOf(ServiceManifestDto dto) {
        return dto.metadata == null ? null : dto.metadata.team;
    }

    /**
     * Les durees du manifeste s'ecrivent en secondes suffixees de "s" (ex.
     * "10s") — voir docs/architecture/06-service-manifest.md. Seul ce
     * format est necessaire aujourd'hui ; d'autres unites rejoindront ce
     * parseur si un besoin reel se presente, jamais par anticipation.
     */
    private static Duration parseSecondsSuffix(String raw, String fieldName) {
        if (raw == null || !raw.endsWith("s")) {
            throw new IllegalArgumentException(
                    "ServiceManifest." + fieldName + " invalide : \"" + raw
                            + "\" — attendu un nombre de secondes suffixe de \"s\" (ex. \"10s\")");
        }
        try {
            return Duration.ofSeconds(Long.parseLong(raw.substring(0, raw.length() - 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ServiceManifest." + fieldName + " invalide : \"" + raw + "\"", e);
        }
    }

    public static ServiceStatusDto toDto(ServiceStatus status) {
        ServiceStatusDto dto = new ServiceStatusDto();
        dto.phase = status.phase().name();
        dto.observedGeneration = status.observedGeneration();
        dto.conditions = toConditionDtos(status.conditions());
        return dto;
    }

    private static List<ConditionDto> toConditionDtos(List<Condition> conditions) {
        return conditions.stream().map(ServiceManifestMapper::toDto).toList();
    }

    private static ConditionDto toDto(Condition condition) {
        ConditionDto dto = new ConditionDto();
        dto.type = condition.type();
        dto.status = condition.status().name();
        dto.reason = condition.reason();
        dto.message = condition.message();
        dto.lastTransitionTime = condition.lastTransitionTime().toString();
        return dto;
    }
}



