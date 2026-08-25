-- Los fragmentos con los que se construyo cada respuesta. Se guardan copiados y no por
-- referencia a proposito: si la fuente se vuelve a rastrear o se borra, la bandeja tiene que
-- seguir enseñando con que respondio el asistente aquel dia. Una respuesta mala se revisa
-- contra el texto que de verdad se le paso al modelo, no contra el que hay hoy.
CREATE TABLE mensaje_fuente (
    id         UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID     NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    mensaje_id UUID     NOT NULL REFERENCES mensaje(id) ON DELETE CASCADE,
    numero     SMALLINT NOT NULL,
    titulo     VARCHAR(300),
    url        TEXT,
    texto      TEXT     NOT NULL,
    distancia  REAL     NOT NULL,
    -- El motor recupera cinco y el modelo suele citar dos. Distinguirlo dice si el problema de
    -- una respuesta floja fue la busqueda (no encontro) o la redaccion (lo tuvo y no lo uso).
    citada     BOOLEAN  NOT NULL DEFAULT FALSE
);

CREATE INDEX mensaje_fuente_mensaje ON mensaje_fuente (mensaje_id, numero);

-- Lo que el visitante opina de una respuesta: 1 sirvio, -1 no sirvio. Nulo = no dijo nada,
-- que es lo normal; por eso la columna admite nulos en vez de tener un cero que confunda.
ALTER TABLE mensaje ADD COLUMN calificacion SMALLINT
    CONSTRAINT mensaje_calificacion_valida CHECK (calificacion IN (-1, 1));

ALTER TABLE mensaje_fuente ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensaje_fuente FORCE ROW LEVEL SECURITY;

CREATE POLICY mensaje_fuente_visible ON mensaje_fuente
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
