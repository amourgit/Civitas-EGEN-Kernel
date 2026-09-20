package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ConditionDto;
import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.api.dto.ServiceStatusDto;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;

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
        ServiceRuntime runtime = new ServiceRuntime(
                RuntimeType.valueOf(dto.runtime.type.toUpperCase()),
                dto.runtime.artifact,
                dto.runtime.language);
        DeploymentSpec deployment = new DeploymentSpec(
                dto.deployment.adapter,
                dto.deployment.image,
                dto.deployment.resources.cpu,
                dto.deployment.resources.memory,
                new ReplicaRange(dto.deployment.replicas.min, dto.deployment.replicas.max));

        return new ServiceManifest(id, version, runtime, deployment);
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
