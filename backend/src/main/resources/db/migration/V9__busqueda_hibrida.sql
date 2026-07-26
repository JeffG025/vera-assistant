-- Busqueda por palabra exacta, complementaria a la vectorial. La vectorial entiende sinonimos
-- pero falla con nombres propios, claves de producto y numeros; esta los encuentra tal cual.
ALTER TABLE fragmento ADD COLUMN busqueda tsvector
    GENERATED ALWAYS AS (to_tsvector('spanish'::regconfig, texto)) STORED;

CREATE INDEX fragmento_busqueda ON fragmento USING gin (busqueda);

-- Personalidad del asistente, configurable por negocio.
ALTER TABLE tenant ADD COLUMN asistente_nombre VARCHAR(60)  NOT NULL DEFAULT 'Vera';
ALTER TABLE tenant ADD COLUMN asistente_idioma VARCHAR(20)  NOT NULL DEFAULT 'espanol';
ALTER TABLE tenant ADD COLUMN asistente_tono   TEXT;
ALTER TABLE tenant ADD COLUMN asistente_saludo TEXT;
