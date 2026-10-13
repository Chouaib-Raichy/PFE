# Image Keycloak specifique au cluster local.
#
# Le theme "pfeecom" contient des sous-repertoires (login/resources/css/...) qui
# ne peuvent pas tenir dans un ConfigMap Kubernetes (les cles n'acceptent pas de
# "/"). On "cuit" donc le theme et le royaume dans une image derivee, comme le
# fait deja Docker Compose via des montages de fichiers.
#
# Construction (depuis la RACINE du depot) :
#   docker build -f k8s/keycloak/Dockerfile -t pfe-keycloak-ecom:latest .
#
# Le royaume importe est celui du depot (keycloak/realm-pfeecom.json). Il
# contient des identifiants de DEVELOPPEMENT : cette image reste locale.
FROM quay.io/keycloak/keycloak:26.1

# Theme de connexion personnalise (cosmetique, non sensible).
COPY --chown=keycloak:keycloak keycloak/themes/pfeecom /opt/keycloak/themes/pfeecom

# Royaume importe automatiquement au demarrage (--import-realm).
COPY --chown=keycloak:keycloak keycloak/realm-pfeecom.json /opt/keycloak/data/import/realm-pfeecom.json
