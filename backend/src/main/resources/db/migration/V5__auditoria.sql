-- Bitacora de acciones sensibles. Hereda los permisos por ALTER DEFAULT PRIVILEGES de la V3.
CREATE TABLE auditoria (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        REFERENCES tenant(id) ON DELETE CASCADE,
    usuario_id UUID        REFERENCES usuario(id) ON DELETE SET NULL,
    accion     VARCHAR(60) NOT NULL,
    detalle    TEXT,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX auditoria_tenant_fecha ON auditoria (tenant_id, creado_en DESC);

ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;

CREATE POLICY auditoria_visible ON auditoria
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
