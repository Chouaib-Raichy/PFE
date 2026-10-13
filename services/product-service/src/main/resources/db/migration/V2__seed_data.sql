-- Reprise du catalogue de demonstration existant (identifiants conserves).
-- Les sequences sont repositionnees sur max(id) pour eviter toute collision.

INSERT INTO categories (id, name, description, created_at)
VALUES
    (1, 'Accessories', NULL, now()),
    (2, 'Audio',       NULL, now());

INSERT INTO products (id, name, description, price, stock_quantity, image_url, category_id, category_name, active, created_at, updated_at)
VALUES
    (1, 'Docker Product',   'via compose', 49.99, 12, NULL, NULL, NULL,          TRUE, now(), now()),
    (2, 'Charger',          NULL,          19.99, 30, NULL, 1,    'Accessories', TRUE, now(), now()),
    (3, 'Casque Bluetooth', NULL,          89.90, 15, NULL, 1,    'Accessories', TRUE, now(), now());

SELECT setval('category_seq', (SELECT max(id) FROM categories), true);
SELECT setval('product_seq',  (SELECT max(id) FROM products),  true);
