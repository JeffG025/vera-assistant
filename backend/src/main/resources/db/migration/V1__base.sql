-- Vera - esquema base. Flyway lo aplica como V1 en el incremento 0.2.

-- Busqueda vectorial para los fragmentos de conocimiento (bloque 2).
CREATE EXTENSION IF NOT EXISTS vector;

-- Catalogo de roles del sistema.
CREATE TABLE rol (
    id     SMALLINT    PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(60) NOT NULL
);

-- Un negocio cliente; es la raiz del aislamiento multi-tenant.
CREATE TABLE tenant (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre         VARCHAR(120) NOT NULL,
    slug           VARCHAR(60)  NOT NULL UNIQUE,
    plan           VARCHAR(20)  NOT NULL DEFAULT 'TRIAL',
    estado         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT tenant_plan_valido   CHECK (plan   IN ('TRIAL', 'BASICO', 'PRO', 'ESCALA')),
    CONSTRAINT tenant_estado_valido CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'CANCELADO')),
    CONSTRAINT tenant_slug_formato  CHECK (slug ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$')
);

-- Usuario del panel; tenant_id NULL solo para el SUPERADMIN de la plataforma.
CREATE TABLE usuario (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         REFERENCES tenant(id) ON DELETE CASCADE,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nombre        VARCHAR(120) NOT NULL,
    rol_id        SMALLINT     NOT NULL REFERENCES rol(id),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ultimo_acceso TIMESTAMPTZ
);

-- El email es unico dentro de cada negocio, no globalmente.
CREATE UNIQUE INDEX usuario_email_por_tenant ON usuario (tenant_id, lower(email));

-- Los usuarios de plataforma no tienen tenant, ahi el email si es unico global.
CREATE UNIQUE INDEX usuario_email_plataforma ON usuario (lower(email)) WHERE tenant_id IS NULL;

-- Todo acceso a datos filtra por tenant.
CREATE INDEX usuario_tenant ON usuario (tenant_id);

INSERT INTO rol (id, codigo, nombre) VALUES
    (1, 'SUPERADMIN', 'Administrador de la plataforma'),
    (2, 'OWNER',      'Dueno del negocio'),
    (3, 'AGENT',      'Agente');
