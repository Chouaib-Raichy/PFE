-- Schema du service client (PostgreSQL).
-- L'unicite de l'email est garantie par une contrainte UNIQUE sur email_key,
-- en plus du controle applicatif (message 409 explicite).

CREATE SEQUENCE customer_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE customers (
    id         BIGINT       PRIMARY KEY DEFAULT nextval('customer_seq'),
    first_name VARCHAR(150) NOT NULL,
    last_name  VARCHAR(150) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    email_key  VARCHAR(150) NOT NULL,
    phone      VARCHAR(30),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_customers_email_key UNIQUE (email_key)
);
