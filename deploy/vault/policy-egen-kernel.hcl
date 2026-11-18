# Politique Vault du Kernel EGEN (docs/architecture/19, phase P0, étape P0-4 ; docs/architecture/14).
#
# Le Kernel ne vérifie que l'EXISTENCE d'un secret : il appelle « GET secret/metadata/<chemin> » (VaultSecretsAdapter).
# Il ne lit JAMAIS une valeur : cette politique ne donne donc AUCUN droit sur « secret/data/* », ni d'écriture, ni
# d'administration (« sys/* »). Un jeton qui porte cette politique et qui fuit ne révèle aucun secret.
path "secret/metadata/*" {
  capabilities = ["read"]
}
