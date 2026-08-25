-- Datos de contacto que deja un visitante cuando el asistente no supo responderle. Ese es el
-- momento con mas valor de toda la conversacion: hay una pregunta que el negocio si sabe
-- contestar y una persona al otro lado esperando la respuesta.
--
-- Se llama contacto y no lead porque el resto del esquema esta en español, y porque lead es
-- ademas el nombre de una funcion de ventana en Postgres.
CREATE TABLE contacto (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    -- Sin ON DELETE CASCADE a proposito: si algun dia se purgan conversaciones viejas, el
    -- contacto no puede irse con ellas. Queda la persona aunque se pierda el hilo.
    conversacion_id UUID         REFERENCES conversacion(id) ON DELETE SET NULL,
    nombre          VARCHAR(120) NOT NULL,
    email           VARCHAR(180) NOT NULL,
    telefono        VARCHAR(40),
    -- La pregunta que el asistente no supo: es lo que el negocio tiene que responder al llamar.
    pregunta        TEXT,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Estado del aviso al CRM del cliente.
    webhook_estado  VARCHAR(12)  NOT NULL DEFAULT 'SIN_WEBHOOK',
    webhook_detalle TEXT,
    CONSTRAINT contacto_webhook_estado
        CHECK (webhook_estado IN ('SIN_WEBHOOK', 'PENDIENTE', 'ENVIADO', 'FALLO'))
);

CREATE INDEX contacto_tenant_fecha ON contacto (tenant_id, creado_en DESC);

-- No todos los negocios quieren pedir datos: el que vende servicio caro si, la tienda que solo
-- resuelve dudas no. Apagado por omision, porque pedir el correo sin permiso es intrusivo.
ALTER TABLE tenant ADD COLUMN capturar_contactos BOOLEAN NOT NULL DEFAULT FALSE;

-- A donde avisar cuando entra uno. Vacio = solo se guarda y se ve en el panel.
ALTER TABLE tenant ADD COLUMN webhook_url TEXT;

ALTER TABLE contacto ENABLE ROW LEVEL SECURITY;
ALTER TABLE contacto FORCE ROW LEVEL SECURITY;

CREATE POLICY contacto_visible ON contacto
    USING (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('vera.bypass', true) = 'on'
        OR tenant_id = nullif(current_setting('vera.tenant_id', true), '')::uuid
    );
