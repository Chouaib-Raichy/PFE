#!/bin/bash
# Initialisation du conteneur PostgreSQL de TEST (profil "test").
#
# Bases isolees utilisees par les tests d'integration (port hote 5433), jamais
# les bases de demonstration. Chaque base est videe avant chaque test.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE pfe_products_test;
    CREATE DATABASE pfe_customers_test;
EOSQL
