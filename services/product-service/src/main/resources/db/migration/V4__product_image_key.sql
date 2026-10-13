-- Phase 5 : images produit stockees dans un stockage objet S3 (MinIO).
-- image_url reste l'URL exposee au client (catalogue, recherche) ; image_key est
-- la reference INTERNE de l'objet dans le bucket (jamais exposee), ce qui permet
-- de remplacer ou de supprimer proprement l'ancien objet. Migration additive :
-- image_url existait deja, aucune donnee n'est perdue.

ALTER TABLE products ADD COLUMN image_key VARCHAR(300);
