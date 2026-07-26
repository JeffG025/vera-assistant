-- Casos de evaluacion: el "examen" que se le aplica al asistente de cada negocio.
-- debe_saber = false marca las preguntas que DEBE contestar que no sabe. Sin esas, un
-- examen solo mide lo que el bot acierta y nunca detecta cuando inventa.
CREATE TABLE caso_eval (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    pregunta          TEXT         NOT NULL,
    respuesta_esperada TEXT,
    fuente_esperada   VARCHAR(200),
    debe_saber        BOOLEAN      NOT NULL DEFAULT TRUE,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX caso_eval_tenant ON caso_eval (tenant_id, creado_en);

ALTER TABLE caso_eval ENABLE ROW LEVEL SECURITY;
ALTER TABLE caso_eval FORCE ROW LEVEL SECURITY;

CREATE POLICY caso_eval_visible ON caso_eval
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
