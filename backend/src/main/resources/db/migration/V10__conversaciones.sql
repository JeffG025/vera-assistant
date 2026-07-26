-- Conversaciones y su historial. Es lo que da memoria al asistente y, mas adelante,
-- lo que alimenta la bandeja del bloque 6 y las metricas de preguntas sin respuesta.
CREATE TABLE conversacion (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    canal          VARCHAR(20) NOT NULL DEFAULT 'WEB',
    visitante      VARCHAR(100),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT conversacion_canal_valido CHECK (canal IN ('WEB', 'WHATSAPP', 'TELEGRAM', 'PANEL'))
);

CREATE TABLE mensaje (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    conversacion_id UUID        NOT NULL REFERENCES conversacion(id) ON DELETE CASCADE,
    rol             VARCHAR(12) NOT NULL,
    texto           TEXT        NOT NULL,
    tokens          INTEGER     NOT NULL DEFAULT 0,
    sin_informacion BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT mensaje_rol_valido CHECK (rol IN ('USUARIO', 'ASISTENTE'))
);

CREATE INDEX conversacion_tenant_fecha ON conversacion (tenant_id, actualizado_en DESC);
CREATE INDEX mensaje_conversacion ON mensaje (conversacion_id, creado_en);

ALTER TABLE conversacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversacion FORCE ROW LEVEL SECURITY;
ALTER TABLE mensaje ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensaje FORCE ROW LEVEL SECURITY;

CREATE POLICY conversacion_visible ON conversacion
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );

CREATE POLICY mensaje_visible ON mensaje
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
