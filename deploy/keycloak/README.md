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
