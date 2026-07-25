-- Segunda barrera de aislamiento: aunque un query olvide filtrar, la base no entrega la fila.

ALTER TABLE tenant  ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;

-- FORCE es imprescindible: sin el, el dueno de la tabla (el propio usuario de la app) ignora la politica.
ALTER TABLE tenant  FORCE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

-- Login y alta de negocios cruzan inquilinos por definicion: pasan con bypass explicito.
CREATE POLICY tenant_visible ON tenant
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );

CREATE POLICY usuario_visible ON usuario
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
