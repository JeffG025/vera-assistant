-- Una fuente puede tener muchos documentos: un PDF es uno, un sitio rastreado es uno por pagina.
-- Los fragmentos con embeddings (2.3) colgaran de aqui, no de la fuente.
CREATE TABLE documento (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    fuente_id      UUID         NOT NULL REFERENCES fuente(id) ON DELETE CASCADE,
    url            TEXT,
    titulo         VARCHAR(300),
    contenido      TEXT         NOT NULL,
    hash           VARCHAR(64)  NOT NULL,
    caracteres     INTEGER      NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX documento_fuente ON documento (fuente_id);

-- Una URL no se repite dentro de la misma fuente; md5 porque una URL larga no cabe en un indice btree.
CREATE UNIQUE INDEX documento_url_por_fuente ON documento (fuente_id, md5(coalesce(url, '')));

ALTER TABLE documento ENABLE ROW LEVEL SECURITY;
ALTER TABLE documento FORCE ROW LEVEL SECURITY;

CREATE POLICY documento_visible ON documento
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );

-- Mudanza del contenido que ya vivia en la fuente.
INSERT INTO documento (tenant_id, fuente_id, titulo, contenido, hash, caracteres)
SELECT tenant_id, id, nombre, contenido, encode(sha256(contenido::bytea), 'hex'), caracteres
FROM fuente
WHERE contenido IS NOT NULL AND contenido <> '';

ALTER TABLE fuente DROP COLUMN contenido;
ALTER TABLE fuente ADD COLUMN documentos INTEGER NOT NULL DEFAULT 0;

UPDATE fuente f SET documentos = (SELECT count(*) FROM documento d WHERE d.fuente_id = f.id);
