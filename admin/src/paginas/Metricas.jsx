import { useEffect, useState } from 'react'
import { api } from '../api/cliente.js'

const PERIODOS = [
  { dias: 7, texto: '7 días' },
  { dias: 30, texto: '30 días' },
  { dias: 90, texto: '90 días' },
]

function Cifra({ titulo, valor, pie, tono }) {
  return (
    <div className="cifra">
      <span className="tenue">{titulo}</span>
      <strong className={tono ? `valor ${tono}` : 'valor'}>{valor}</strong>
      {pie && <span className="tenue">{pie}</span>}
    </div>
  )
}

export default function Metricas() {
  const [dias, setDias] = useState(30)
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let vigente = true
    setDatos(null)
    api
      .metricas(dias)
      .then((d) => vigente && setDatos(d))
      .catch((e) => vigente && setError(e.message))
    return () => {
      vigente = false
    }
  }, [dias])

  // La escala se calcula sobre el día más movido: si no, un día flojo se ve igual que uno bueno.
  const techo = datos ? Math.max(1, ...datos.porDia.map((d) => d.preguntas)) : 1

  return (
    <>
      <header className="encabezado">
        <h1>Métricas</h1>
        <p>Qué está haciendo Vera por tu negocio, y qué le falta saber</p>
      </header>

      {error && <p className="mensaje-error">{error}</p>}

      <div className="fila-acciones">
        {PERIODOS.map((p) => (
          <button
            key={p.dias}
            type="button"
            className={p.dias === dias ? 'boton' : 'boton secundario'}
            onClick={() => setDias(p.dias)}
          >
            {p.texto}
          </button>
        ))}
      </div>

      {!datos && !error && <p className="tenue">Calculando…</p>}

      {datos && (
        <>
          <section className="tarjeta">
            <h2>Resumen</h2>
            <div className="cifras">
              <Cifra titulo="Conversaciones" valor={datos.conversaciones} />
              <Cifra titulo="Preguntas" valor={datos.preguntas} />
              <Cifra
                titulo="Resueltas"
                valor={datos.preguntas ? `${datos.tasaResolucion}%` : '—'}
                pie={datos.preguntas ? `${datos.sinRespuesta} sin respuesta` : 'todavía sin datos'}
                tono={datos.preguntas && datos.tasaResolucion < 70 ? 'flojo' : 'bien'}
              />
              <Cifra
                titulo="Le sirvió"
                valor={datos.satisfaccion === null ? '—' : `${datos.satisfaccion}%`}
                pie={
                  datos.satisfaccion === null
                    ? 'nadie ha votado'
                    : `${datos.positivas} sí · ${datos.negativas} no`
                }
              />
              <Cifra titulo="Contactos" valor={datos.contactos} pie="dejaron sus datos" />
            </div>
          </section>

          <div className="rejilla">
            <section className="tarjeta">
              <h2>Lo que no supo responder</h2>
              <p className="tenue">
                Es el dato más útil de esta página: cada línea es alguien preguntando algo que tu
                conocimiento cargado no cubre. Súbelo en Fuentes y deja de perderlo.
              </p>

              {datos.preguntasSinRespuesta.length === 0 ? (
                <p className="tenue">Nada pendiente en este periodo.</p>
              ) : (
                <div className="listado">
                  {datos.preguntasSinRespuesta.map((p) => (
                    <div className="fila" key={p.etiqueta}>
                      <div className="fila-datos">
                        <div>{p.etiqueta}</div>
                      </div>
                      <span className="insignia aviso">
                        {p.cuantas} {p.cuantas === 1 ? 'vez' : 'veces'}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="tarjeta">
              <h2>De qué te preguntan</h2>
              <p className="tenue">
                El documento que acabó resolviendo cada respuesta. No es un agrupamiento por
                significado: es de dónde salió lo que Vera contestó.
              </p>

              {datos.temas.length === 0 ? (
                <p className="tenue">Todavía no hay respuestas con fuente citada.</p>
              ) : (
                <div className="listado">
                  {datos.temas.map((t) => (
                    <div className="fila" key={t.etiqueta}>
                      <div className="fila-datos">
                        <div>{t.etiqueta}</div>
                      </div>
                      <span className="insignia">{t.cuantas}</span>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>

          <section className="tarjeta">
            <h2>Actividad por día</h2>
            <div className="barras">
              {datos.porDia.map((d) => (
                <div className="barra-dia" key={d.dia} title={`${d.dia}: ${d.preguntas} preguntas, ${d.sinRespuesta} sin respuesta`}>
                  <div className="barra-pila" style={{ height: `${(d.preguntas / techo) * 100}%` }}>
                    <div
                      className="barra-fallo"
                      style={{
                        height: d.preguntas ? `${(d.sinRespuesta / d.preguntas) * 100}%` : '0%',
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
            <p className="tenue">
              Cada barra es un día. La parte de arriba, en ámbar, es lo que no supo responder.
            </p>
          </section>
        </>
      )}
    </>
  )
}
