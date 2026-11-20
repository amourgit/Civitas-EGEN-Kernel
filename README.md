# EGEN Kernel — plateforme de gouvernance fédérée

**Le Kernel fédère les Control Planes, organise leurs relations et coordonne leurs interactions. Il ne remplace pas leur gouvernance autonome et n'exécute pas les opérations confiées aux moteurs spécialisés.**

EGEN, développé par CIVITAS Africa, organise les systèmes d'une organisation (par exemple une université) en **écosystèmes** : Métier, Transversal, Intelligence et Agents.

Chaque écosystème est gouverné par un **Control Plane autonome** : un système qui reçoit les intentions de gouvernance de son périmètre, applique ses politiques, compose les capacités techniques nécessaires, délègue l'exécution aux moteurs spécialisés et suit les résultats.

Le **Federation Plane** organise la fédération de plusieurs Control Planes. Il gère leurs relations fédérales, leur enregistrement, leur identité et leur confiance, la découverte de leurs capacités ainsi que la coordination des opérations qui concernent plusieurs Control Planes.

Le **EGEN Kernel**, développé par CIVITAS Africa, est l'implémentation de référence de ce **Federation Plane**. Il ne se substitue pas aux Control Planes et ne centralise pas la gouvernance de leurs écosystèmes.

Les Control Planes restent libres de développer leurs propres intégrations, d'adopter des capacités techniques disponibles transversalement en déployant leurs propres instances, ou de combiner les deux approches.

## Ce que le Kernel n'est pas

- Pas un Control Plane métier ou un Control Plane d'écosystème : il fédère les Control Planes autonomes.
- Pas un moteur de déploiement, de découverte, de messagerie ou de workflow : il s'appuie sur des solutions spécialisées.
- Pas le point d'exécution central des opérations locales des Control Planes.
- Pas une copie centrale des ressources et des données des écosystèmes : chaque Control Plane conserve la maîtrise de son état local et de ses ressources. Le Kernel conserve uniquement les informations nécessaires à la fédération.
- Pas dépendant d'une technologie ou d'un langage unique pour qu'un Control Plane puisse participer à la fédération : seuls les contrats fédéraux comptent.
- Pas porteur de concepts métier (cellule, tenant, catalogue, etc.) : il reste neutre vis-à-vis des domaines fonctionnels.


## Règles non négociables

- La documentation précède le code : le contrat s'écrit avant l'implémentation.
- Adopter avant de coder : une solution éprouvée existante prime sur du code écrit ici (décision D-07).
- Aucun secret (jeton, mot de passe, clé) dans un fichier, un commit ou un document.
- `main` ne reçoit que des étapes vérifiées par des tests ; il est toujours à jour et vérifié.

Licence : à définir (point ouvert PO-09 dans [docs/16-risques-et-verifications.md](docs/16-risques-et-verifications.md)).

Contribuer : voir [CONTRIBUTING.md](CONTRIBUTING.md).
