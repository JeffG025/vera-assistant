-- Fragmentos con embedding: la unidad que se recupera al responder.
-- Se guardan titulo y url copiados del documento para poder citar la fuente sin otro JOIN.
CREATE TABLE fragmento (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    fuente_id    UUID         NOT NULL REFERENCES fuente(id) ON DELETE CASCADE,
    documento_id UUID         NOT NULL REFERENCES documento(id) ON DELETE CASCADE,
    orden        INTEGER      NOT NULL,
    texto        TEXT         NOT NULL,
    titulo       VARCHAR(300),
    url          TEXT,
    embedding    vector(768)  NOT NULL,
    modelo       VARCHAR(60)  NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX fragmento_documento ON fragmento (documento_id);
CREATE INDEX fragmento_tenant ON fragmento (tenant_id);

-- HNSW con distancia coseno: es la que usa la busqueda por significado.
CREATE INDEX fragmento_hnsw ON fragmento USING hnsw (embedding vector_cosine_ops);

ALTER TABLE fragmento ENABLE ROW LEVEL SECURITY;
ALTER TABLE fragmento FORCE ROW LEVEL SECURITY;

CREATE POLICY fragmento_visible ON fragmento
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
