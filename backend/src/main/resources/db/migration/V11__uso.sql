-- El consumo se calcula sumando los mensajes reales en vez de llevar contadores aparte:
-- una sola fuente de verdad, imposible que se desincronice. Este indice es lo que lo hace barato.
CREATE INDEX mensaje_tenant_fecha ON mensaje (tenant_id, creado_en DESC);
