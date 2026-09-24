package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.event.TechnicalEvent;

/** Callback invoque par un {@link MessagingPort} a la reception d'un evenement. */
@FunctionalInterface
public interface EventHandler {
    void onEvent(TechnicalEvent event);
}
