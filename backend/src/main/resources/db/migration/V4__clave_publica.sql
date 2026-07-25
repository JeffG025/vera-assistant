-- La clave publica es lo que viaja en el <script> del sitio del cliente; no autoriza nada del panel.
ALTER TABLE tenant ADD COLUMN clave_publica VARCHAR(64);

UPDATE tenant SET clave_publica = 'pk_' || replace(gen_random_uuid()::text, '-', '')
WHERE clave_publica IS NULL;

ALTER TABLE tenant ALTER COLUMN clave_publica SET NOT NULL;
CREATE UNIQUE INDEX tenant_clave_publica ON tenant (clave_publica);

-- Lista separada por comas de dominios donde la clave puede usarse; vacio = ninguno todavia.
ALTER TABLE tenant ADD COLUMN dominios_permitidos TEXT;
