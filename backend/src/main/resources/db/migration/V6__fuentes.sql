-- Fuentes de conocimiento del negocio. El texto extraido vive aqui; el troceado y los
-- embeddings llegan en el incremento 2.3 y apuntaran a esta tabla.
CREATE TABLE fuente (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    tipo           VARCHAR(20)  NOT NULL,
    nombre         VARCHAR(200) NOT NULL,
    origen         TEXT,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    contenido      TEXT,
    caracteres     INTEGER      NOT NULL DEFAULT 0,
    error          TEXT,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fuente_tipo_valido   CHECK (tipo   IN ('PDF', 'TEXTO', 'MARKDOWN', 'URL')),
    CONSTRAINT fuente_estado_valido CHECK (estado IN ('PENDIENTE', 'PROCESANDO', 'LISTA', 'ERROR'))
);

CREATE INDEX fuente_tenant_fecha ON fuente (tenant_id, creado_en DESC);

ALTER TABLE fuente ENABLE ROW LEVEL SECURITY;
ALTER TABLE fuente FORCE ROW LEVEL SECURITY;

CREATE POLICY fuente_visible ON fuente
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
