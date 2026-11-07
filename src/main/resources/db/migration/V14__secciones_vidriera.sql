ALTER TABLE productos ADD COLUMN publico VARCHAR(20);

ALTER TABLE vidrieras
    ADD COLUMN portadas TEXT,
    ADD COLUMN mostrar_rubros BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rubros VARCHAR(200);

UPDATE vidrieras
SET portadas = portada_url
WHERE portada_url IS NOT NULL AND portada_url <> '';
