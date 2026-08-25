-- El free tier no aguanta el examen completo: cada caso positivo gasta dos llamadas al modelo.
-- Marcar un subconjunto esencial permite correr la evaluacion a diario sin clave de pago;
-- el set grande queda para cuando la haya.
ALTER TABLE caso_eval ADD COLUMN esencial BOOLEAN NOT NULL DEFAULT FALSE;

-- Sin saber que conjunto se corrio, dos corridas no se pueden comparar: 6 casos y 16 casos
-- dan puntajes que no significan lo mismo.
ALTER TABLE corrida_eval ADD COLUMN conjunto VARCHAR(20) NOT NULL DEFAULT 'completo';
