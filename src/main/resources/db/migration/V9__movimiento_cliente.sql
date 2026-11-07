ALTER TABLE movimientos_caja
    ADD COLUMN cliente_id BIGINT REFERENCES clientes (id);
