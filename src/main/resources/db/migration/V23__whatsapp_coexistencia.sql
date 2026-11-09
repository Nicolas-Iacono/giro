ALTER TABLE comercios ADD COLUMN wa_nombre VARCHAR(160);
ALTER TABLE comercios ADD COLUMN wa_tipo VARCHAR(20);
ALTER TABLE comercios ADD COLUMN wa_estado VARCHAR(20);
ALTER TABLE comercios ADD COLUMN wa_conectado_en TIMESTAMP;
ALTER TABLE comercios ADD COLUMN wa_sincronizado_en TIMESTAMP;
ALTER TABLE comercios ADD COLUMN wa_token_vence_en TIMESTAMP;
ALTER TABLE comercios ADD COLUMN wa_revocado_en TIMESTAMP;
ALTER TABLE comercios ADD COLUMN wa_historial VARCHAR(20);
ALTER TABLE comercios ADD COLUMN wa_aviso VARCHAR(400);

ALTER TABLE whatsapp_contactos ADD COLUMN ultimo_cliente_en TIMESTAMP;

ALTER TABLE whatsapp_mensajes ADD COLUMN meta_id VARCHAR(128);
ALTER TABLE whatsapp_mensajes ADD COLUMN estado VARCHAR(16);
CREATE UNIQUE INDEX uq_whatsapp_mensaje_meta ON whatsapp_mensajes (meta_id) WHERE meta_id IS NOT NULL;

CREATE TABLE whatsapp_vinculos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    clave VARCHAR(64) NOT NULL UNIQUE,
    vence_en TIMESTAMP NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE whatsapp_eventos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dedupe VARCHAR(64) NOT NULL UNIQUE,
    cuerpo TEXT NOT NULL,
    estado VARCHAR(16) NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 0,
    error VARCHAR(80),
    creado_en TIMESTAMP NOT NULL DEFAULT NOW(),
    procesado_en TIMESTAMP
);

CREATE INDEX ix_whatsapp_eventos_estado ON whatsapp_eventos (estado, id);
