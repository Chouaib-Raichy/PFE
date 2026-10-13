-- Reprise des clients de demonstration existants (identifiants conserves).
-- Les sequences sont repositionnees sur max(id) pour eviter toute collision.

INSERT INTO customers (id, first_name, last_name, email, email_key, phone, created_at, updated_at)
VALUES
    (1, 'Yassine', 'Alami',   'yassine.alami@demo.com', 'yassine.alami@demo.com', '0661234567', now(), now()),
    (2, 'Sarah',   'Berrada', 'sarah.berrada@demo.com', 'sarah.berrada@demo.com', '0655443322', now(), now());

SELECT setval('customer_seq', (SELECT max(id) FROM customers), true);
