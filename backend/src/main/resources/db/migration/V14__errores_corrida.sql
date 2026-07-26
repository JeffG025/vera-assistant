-- Los casos que no se pudieron ejecutar tienen que verse. Sin esta columna, una corrida
-- donde fallo medio examen reportaba el puntaje de la otra mitad como si fuera el total.
ALTER TABLE corrida_eval ADD COLUMN errores INTEGER NOT NULL DEFAULT 0;
