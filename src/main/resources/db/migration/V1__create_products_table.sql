CREATE TABLE products (
    id          UUID            NOT NULL,
    sku         VARCHAR(50)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description TEXT,
    price       NUMERIC(19, 4)  NOT NULL CHECK (price > 0),
    stock       INTEGER         NOT NULL DEFAULT 0 CHECK (stock >= 0),
    category    VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT pk_products     PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);

CREATE INDEX idx_products_sku        ON products (sku);
CREATE INDEX idx_products_category   ON products (category);
CREATE INDEX idx_products_deleted_at ON products (deleted_at);
CREATE INDEX idx_products_created_at ON products (created_at DESC);
