-- File d'indexation Elasticsearch (outbox transactionnel).
-- Une ligne = un produit dont le document de recherche doit etre resynchronise.
-- La file est ecrite dans la MEME transaction que le produit (voir
-- ProductServiceImpl) : aucune modification n'est perdue si Elasticsearch est
-- momentanement indisponible. Le traitement differe relit le produit courant
-- puis indexe ou desindexe (idempotent, aucune duplication possible : l'id du
-- document de recherche est l'id du produit).
--
-- Aucune cle etrangere : un produit supprime doit aussi etre desindexe, donc la
-- file doit pouvoir survivre a la disparition de la ligne products.
CREATE TABLE product_search_outbox (
    product_id BIGINT    PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
