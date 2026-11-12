# Realm Keycloak d'essai pour le Kernel EGEN

`egen-realm-test.json` crée le realm `egen` avec le rôle `egen-admin`, trois clients à compte de service
(`platform-operator-service` et `deploy-pipeline` portent le rôle, `catalog-service` ne le porte pas) et, sur chacun, le
mapper qui recopie les rôles du realm dans la claim `groups` que le Kernel lit
([docs/architecture/14-securite.md](../../docs/architecture/14-securite.md), « Jetons attendus par le Kernel »).

**Pour des essais seulement : les secrets des clients sont dans le fichier.** Ne jamais l'utiliser en production.

```
docker run -d --name egen-kc -p 8180:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin-test \
  -v "$PWD/deploy/keycloak/egen-realm-test.json:/opt/keycloak/data/import/egen-realm.json:ro" \
  quay.io/keycloak/keycloak:26.8.2 start-dev --import-realm
```

Variables du Kernel : `EGEN_OIDC_ISSUER_URL=http://localhost:8180/realms/egen` et
`EGEN_OIDC_JWKS_URL=http://localhost:8180/realms/egen/protocol/openid-connect/certs`.

## Scripts de la vérification V10 ([docs/architecture/19](../../docs/architecture/19-feuille-de-route.md))

- `v10-setup.sh` : lance Keycloak (realm d'essai) et une base PostgreSQL dans Docker, écrit `/tmp/v10/news.yaml`.
- `v10-claims.sh CLIENT SECRET` : affiche les claims utiles du jeton d'un client (jamais le jeton lui-même).
- `v10-run.sh` : avec le Kernel démarré, affiche pour chaque client les codes HTTP obtenus en lecture et en déclaration, et la liste des porteurs du rôle.
- `v3c-run.sh` : avec le Kernel de V10 démarré, vérifie qu'un corps de réponse de 1200 caractères est coupé à 500 caractères (suffixe ` [tronque]`) dans le journal et dans la Condition (contrat K8).
