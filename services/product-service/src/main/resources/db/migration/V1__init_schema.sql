-- Schema du service produit (PostgreSQL).
-- Relations reelles : products.category_id -> categories.id (cle etrangere),
-- unicite du nom de categorie, prix et stock positifs (contraintes CHECK).

CREATE SEQUENCE category_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE product_seq  START WITH 1 INCREMENT BY 1;

CREATE TABLE categories (
    id          BIGINT       PRIMARY KEY DEFAULT nextval('category_seq'),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE products (
    id             BIGINT        PRIMARY KEY DEFAULT nextval('product_seq'),
    name           VARCHAR(150)  NOT NULL,
    description    VARCHAR(1000),
    price          NUMERIC(12, 2) NOT NULL,
    stock_quantity INTEGER       NOT NULL,
    image_url      VARCHAR(1000),
    category_id    BIGINT,
    category_name  VARCHAR(100),
    active         BOOLEAN,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_stock CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_products_category ON products (category_id);
