CREATE TABLE whatsapp_contactos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    telefono VARCHAR(32) NOT NULL,
    nombre VARCHAR(160),
    ultimo_texto VARCHAR(500),
    ultimo_en TIMESTAMP,
    no_leidos INTEGER NOT NULL DEFAULT 0,
    bot_activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_whatsapp_contacto UNIQUE (comercio_id, telefono)
);

CREATE TABLE whatsapp_mensajes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contacto_id BIGINT NOT NULL REFERENCES whatsapp_contactos (id),
    direccion VARCHAR(12) NOT NULL,
    origen VARCHAR(12) NOT NULL,
    texto VARCHAR(4000) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_whatsapp_mensajes_contacto ON whatsapp_mensajes (contacto_id, creado_en);
