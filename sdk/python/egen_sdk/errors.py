"""Exceptions du SDK (voir docs/architecture/12-communication-fabric.md)."""


class EgenError(Exception):
    """Erreur du SDK : plan de controle injoignable, reponse illisible, ..."""


class CircuitOpenError(EgenError):
    """Le circuit du service cible est ouvert : appel rejete SANS aucun acces reseau."""

    def __init__(self, service: str):
        super().__init__(f"Circuit ouvert pour le service {service} : appel rejete sans acces reseau")
        self.service = service


class NoInstanceError(EgenError):
    """Aucune instance saine n'est resolue pour ce service — aucun appel n'a ete tente."""

    def __init__(self, service: str):
        super().__init__(f"Aucune instance saine pour le service {service}")
        self.service = service


class CallFailedError(EgenError):
    """Toutes les tentatives ont echoue SANS reponse HTTP (``reason`` : "timeout" ou "network").

    Quand une reponse existe — meme un 503 — elle est renvoyee telle quelle, jamais en exception.
    """

    def __init__(self, service: str, reason: str, attempts: int):
        super().__init__(f"Appel de {service} en echec apres {attempts} tentative(s) : {reason}")
        self.service = service
        self.reason = reason
        self.attempts = attempts


class PublishRejectedError(EgenError):
    """Le Kernel a repondu et N'A PAS publie l'evenement (docs/architecture/12).

    Reponse definitive (400 corps invalide, 401, 403 type non annonce ou appelant non autorise — jamais
    reessayee), ou echec reessayable persistant une fois la politique appliquee (503 messagerie
    indisponible...). Contrairement a ``call``, ``publish`` ne renvoie jamais une reponse d'echec comme
    une valeur.
    """

    def __init__(self, status: int, message: str, attempts: int):
        super().__init__(f"Publication refusee par le Kernel (HTTP {status}) apres {attempts} tentative(s) : {message}")
        self.status = status
        self.attempts = attempts
