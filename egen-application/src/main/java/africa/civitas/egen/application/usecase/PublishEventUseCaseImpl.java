package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.OwnerTeam;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class PublishEventUseCaseImpl implements PublishEventUseCase {

    private final RegistryStorePort registryStorePort;
    private final MessagingPort messagingPort;

    public PublishEventUseCaseImpl(RegistryStorePort registryStorePort, MessagingPort messagingPort) {
        this.registryStorePort = Objects.requireNonNull(registryStorePort, "registryStorePort");
        this.messagingPort = Objects.requireNonNull(messagingPort, "messagingPort");
    }

    @Override
    public TechnicalEvent publish(PublishEventCommand command, Predicate<OwnerTeam> callerMayActFor) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(callerMayActFor, "callerMayActFor");

        List<DesiredState> publishers = publishersOf(command.type());
        if (publishers.isEmpty()) {
            throw new EventPublicationNotAllowedException(
                    "Aucun service declare ne publie le type d'evenement \"" + command.type() + "\"");
        }
        if (publishers.stream().noneMatch(p -> callerMayActFor.test(p.manifest().ownerTeam()))) {
            throw new EventPublicationNotAllowedException(
                    "L'appelant n'est autorise sur aucun service qui publie \"" + command.type() + "\"");
        }

        String correlationId = command.correlationId() == null || command.correlationId().isBlank()
                ? UUID.randomUUID().toString() : command.correlationId();
        String causationId = command.causationId() == null || command.causationId().isBlank()
                ? null : command.causationId();
        TechnicalEvent event = new TechnicalEvent(UUID.randomUUID().toString(), command.source(),
                command.type(), Instant.now(), correlationId, causationId, command.data());

        // Le sujet est le type lui-meme (docs/architecture/12, "Flux d'evenements") ; data n'est jamais lu.
        messagingPort.publish(event, command.type());
        return event;
    }

    private List<DesiredState> publishersOf(String type) {
        return registryStorePort.findAllIds().stream()
                .map(registryStorePort::findById)
                .flatMap(java.util.Optional::stream)
                .filter(state -> state.manifest().events().publishes().stream()
                        .map(EventSpec.Published::type).anyMatch(type::equals))
                .toList();
    }
}
