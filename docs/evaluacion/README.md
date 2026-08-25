# Conjuntos de evaluación

El examen que se le aplica al asistente de un negocio. Cada archivo es el conjunto de un
inquilino demo y se carga con `POST /api/evaluacion/casos/csv`.

| Archivo | Qué es |
|---|---|
| `casos-patitas-felices.csv` | Las preguntas del examen |
| `corpus-patitas.txt` | Los documentos que el asistente debe conocer, en bloques `== título ==` |

El corpus vive aquí y no solo en una base local para que las pruebas puedan armar el negocio
demo desde cero — y para que un cambio en el examen se vea en un diff.

## Formato

`pregunta,respuesta_esperada,fuente_esperada,debe_saber,esencial`

| Columna | Qué significa |
|---|---|
| `pregunta` | Lo que escribiría un visitante. Obligatoria. |
| `respuesta_esperada` | El dato correcto. Vacía en los casos negativos. |
| `fuente_esperada` | **Título exacto del documento** que debería recuperarse. Si no coincide con un documento real, ese caso nunca puede aprobar recuperación. |
| `debe_saber` | `no` marca los casos donde lo correcto es admitir que no sabe. |
| `esencial` | `si` incluye el caso en el conjunto que se corre a diario. |

Sin filas `debe_saber = no` el examen solo mide aciertos y jamás detecta cuando el
asistente inventa.

## Por qué hay dos conjuntos

Cada caso positivo gasta **dos** llamadas al modelo: una para responder y otra para el juez
de fidelidad. El conjunto completo no cabe en el free tier de Gemini — la corrida se queda
a la mitad y el resultado no sirve.

- **`esencial`** (lo que corre por omisión): un puñado de casos que cubre los tres documentos
  del corpus y las tres métricas. Cabe de sobra en el free tier y es el que corre en cada
  cambio de prompt o de índice para detectar regresiones.
- **`completo`**: hay que pedirlo a propósito (`POST /api/evaluacion/corridas?conjunto=completo`)
  y necesita clave de pago.

Cada corrida guarda con qué conjunto se hizo: un puntaje de 6 casos y uno de 16 no son
comparables entre sí.

## Corrida de referencia

`casos-patitas-felices.csv`, conjunto esencial, 2026-07-31 — 6 casos, 10 comprobaciones,
`gemini-flash-latest`, 3 277 tokens, 2 min 13 s, **100 %** sin errores.

Ese 100 % dice que no hay regresión, no que el asistente sea infalible: el corpus demo son
tres documentos cortos. El conjunto discrimina de verdad cuando los inquilinos demo del
bloque 8 traigan un corpus grande.

## El examen en la integración continua

Son dos pruebas distintas, a propósito:

| Prueba | Cuándo corre | Qué garantiza |
|---|---|---|
| `ExamenEnCiTest` | En cada push, sin clave | Que el conjunto se siembra desde el repo y el examen corre entero, sin casos sin evaluar |
| `ExamenRealTest` | A mano o los lunes, con clave | Recuperación ≥ 4/4, fidelidad ≥ 3/4, abstención perfecta y cero errores |

La separación no es capricho. **Sin clave los embeddings no son semánticos y la recuperación
cae a 0 de 4** — medido, no supuesto: lo único que empareja pregunta y documento es la
búsqueda de texto completo, y las preguntas del examen están escritas como las diría un
cliente, no copiando las palabras del documento. Poner un umbral de calidad sobre esa corrida
sería un candado que no cierra nada.

Para correr el examen real en tu máquina, con la clave ya en `application-local.properties`:

```bash
cd backend && VERA_EXAMEN_REAL=1 ./mvnw -o -Dtest=ExamenRealTest -DfailIfNoTests=false test
```

En GitHub, el job `Examen de calidad (modelo real)` necesita el secreto `GEMINI_API_KEY`. Si
falta, la prueba falla diciendo que no midió nada — nunca pasa en verde sin haber medido.
