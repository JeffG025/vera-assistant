-- Postgres ignora RLS por completo para superusuarios, y FORCE no lo cambia.
-- La aplicacion baja a este rol sin privilegios especiales para que las politicas si apliquen.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vera_app') THEN
        CREATE ROLE vera_app NOLOGIN;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO vera_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO vera_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO vera_app;

-- Lo que creen las migraciones futuras hereda el permiso sin tener que acordarse.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vera_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO vera_app;
