# Exemple de fichier de variables pour deploy/nomad/egen-kernel.nomad.hcl.
# Copiez-le, remplacez les valeurs ci-dessous, et ne versionnez JAMAIS le fichier réel (il contient un mot de passe).
oidc_issuer_url      = "http://localhost:8180/realms/egen"
oidc_jwks_url        = "http://localhost:8180/realms/egen/protocol/openid-connect/certs"
registry_db_password = "remplacez-moi"
