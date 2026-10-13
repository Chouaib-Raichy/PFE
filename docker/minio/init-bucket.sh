#!/bin/sh
# Initialisation du stockage objet MinIO (Phase 5) :
#   - cree le bucket des images produit (PRIVE par defaut : aucun acces anonyme) ;
#   - cree un utilisateur applicatif a privilege MINIMAL, autorise uniquement sur
#     ce bucket (aucun acces administrateur ni aux autres buckets) ;
#   - attache la politique ainsi generee.
# Idempotent : peut etre rejoue sans erreur fatale.
set -e

: "${MINIO_BUCKET:?MINIO_BUCKET requis}"
: "${MINIO_ROOT_USER:?MINIO_ROOT_USER requis}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD requis}"
: "${MINIO_APP_ACCESS_KEY:?MINIO_APP_ACCESS_KEY requis}"
: "${MINIO_APP_SECRET_KEY:?MINIO_APP_SECRET_KEY requis}"

echo "Attente de MinIO..."
until mc alias set app "http://minio:9000" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  sleep 2
done

echo "Creation du bucket ${MINIO_BUCKET} (prive)..."
mc mb --ignore-existing "app/${MINIO_BUCKET}"

echo "Generation de la politique a privilege minimal..."
sed "s|__BUCKET__|${MINIO_BUCKET}|g" /init/product-images-policy.json > /tmp/policy.json

echo "Creation de l'utilisateur applicatif ${MINIO_APP_ACCESS_KEY}..."
mc admin user add app "$MINIO_APP_ACCESS_KEY" "$MINIO_APP_SECRET_KEY" >/dev/null 2>&1 || true
mc admin policy create app pfeecom-product-images /tmp/policy.json >/dev/null 2>&1 || true
mc admin policy attach app pfeecom-product-images --user "$MINIO_APP_ACCESS_KEY" >/dev/null 2>&1 || true

echo "Stockage objet pret : bucket=${MINIO_BUCKET}, utilisateur=${MINIO_APP_ACCESS_KEY}"
