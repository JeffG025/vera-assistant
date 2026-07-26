-- Historial de examenes. Guardar cada corrida completa es lo que permite comparar dos
-- versiones del prompt y demostrarle a un cliente que la calidad no bajo.
CREATE TABLE corrida_eval (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    total             INTEGER     NOT NULL,
    recuperacion_ok   INTEGER     NOT NULL,
    recuperacion_base INTEGER     NOT NULL,
    fidelidad_ok      INTEGER     NOT NULL,
    fidelidad_base    INTEGER     NOT NULL,
    abstencion_ok     INTEGER     NOT NULL,
    abstencion_base   INTEGER     NOT NULL,
    puntaje           NUMERIC(5,2) NOT NULL,
    modelo            VARCHAR(60),
    umbral            NUMERIC(5,3),
    tokens            INTEGER     NOT NULL DEFAULT 0,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE resultado_eval (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID        NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    corrida_id         UUID        NOT NULL REFERENCES corrida_eval(id) ON DELETE CASCADE,
    pregunta           TEXT        NOT NULL,
    respuesta_obtenida TEXT,
    respuesta_esperada TEXT,
    fuente_esperada    VARCHAR(200),
    fuentes_obtenidas  TEXT,
    debe_saber         BOOLEAN     NOT NULL,
    sin_informacion    BOOLEAN     NOT NULL,
    recuperacion_ok    BOOLEAN,
    fidelidad_ok       BOOLEAN,
    motivo             TEXT,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX corrida_eval_tenant ON corrida_eval (tenant_id, creado_en DESC);
CREATE INDEX resultado_eval_corrida ON resultado_eval (corrida_id);

ALTER TABLE corrida_eval   ENABLE ROW LEVEL SECURITY;
ALTER TABLE corrida_eval   FORCE  ROW LEVEL SECURITY;
ALTER TABLE resultado_eval ENABLE ROW LEVEL SECURITY;
ALTER TABLE resultado_eval FORCE  ROW LEVEL SECURITY;

CREATE POLICY corrida_eval_visible ON corrida_eval
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );

CREATE POLICY resultado_eval_visible ON resultado_eval
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
