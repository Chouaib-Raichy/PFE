#!/bin/bash
# Initialisation du conteneur PostgreSQL e-commerce (profil docker/demo).
#
# Cree une base ET un role DEDIE par service relationnel :
#   - product-service  -> base pfe_products  (role product_svc)
#   - customer-service -> base pfe_customers (role customer_svc)
#
# Aucun port n'est publie sur l'hote : la base n'est joignable que depuis le
# reseau interne pfe-net. Les mots de passe viennent de l'environnement.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE product_svc  LOGIN PASSWORD '${PRODUCT_DB_PASSWORD:-product}';
    CREATE ROLE customer_svc LOGIN PASSWORD '${CUSTOMER_DB_PASSWORD:-customer}';
    CREATE DATABASE pfe_products  OWNER product_svc;
    CREATE DATABASE pfe_customers OWNER customer_svc;
EOSQL
