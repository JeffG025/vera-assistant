import { useEffect, useState } from 'react'
import { api } from '../api/cliente.js'

const VEREDICTOS = { MEJORO: 'ok', REGRESION: 'error', IGUAL: 'aviso' }
const NOTAS = { ACIERTO: 'ok', FALLO: 'error', ERROR: 'aviso' }

function fecha(iso) {
  return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
}

function etiqueta(c) {
  return `${fecha(c.fecha)} · ${c.conjunto} · ${c.puntaje}`
}

// Una corrida a medias se tiene que ver a medias, aunque su puntaje diga 100 %.
function estado(c) {
  if (c.completa) return 'completa'
  if (c.errores > 0) return `${c.errores} sin evaluar`
  return `solo ${c.evaluados} de ${c.total} evaluados`
}

export default function Calidad() {
  const [corridas, setCorridas] = useState(null)
  const [casos, setCasos] = useState([])
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [corriendo, setCorriendo] = useState(false)
  const [abierta, setAbierta] = useState(null)
  const [detalle, setDetalle] = useState([])
  const [nueva, setNueva] = useState('')
  const [base, setBase] = useState('')
  const [comparacion, setComparacion] = useState(null)

  useEffect(() => {
    recargar()
  }, [])

  async function recargar() {
    try {
      const [lista, set] = await Promise.all([api.corridas(), api.casosEval()])
      setCorridas(lista)
      setCasos(set)
    } catch (e) {
      setError(e.message)
    }
  }

  async function correr(conjunto) {
    setCorriendo(true)
    setError(null)
    setAviso(null)
    try {
      const r = await api.correrExamen(conjunto)
      setAviso(
        r.completa
          ? `Examen terminado: ${r.puntaje} sobre ${r.total} casos`
          : `Examen incompleto (${estado(r)}): el puntaje no vale como referencia`,
      )
      await recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setCorriendo(false)
    }
  }

  async function verCasos(corrida) {
    if (abierta === corrida.id) return setAbierta(null)
    setAbierta(corrida.id)
    setDetalle([])
    try {
      setDetalle(await api.detalleCorrida(corrida.id))
    } catch (e) {
      setError(e.message)
    }
  }

  async function comparar() {
    setError(null)
    setComparacion(null)
    try {
      setComparacion(await api.compararCorridas(nueva, base))
    } catch (e) {
      setError(e.message)
    }
  }

  if (!corridas) return <p className="tenue">Cargando…</p>

  const esenciales = casos.filter((c) => c.esencial).length
  const conjuntoDeNueva = corridas.find((c) => c.id === nueva)?.conjunto
  // Solo se compara contra una corrida del mismo conjunto: 6 casos y 16 no miden lo mismo.
  const referencias = corridas.filter((c) => c.id !== nueva && (!conjuntoDeNueva || c.conjunto === conjuntoDeNueva))

  return (
    <>
      <header className="encabezado">
        <h1>Calidad</h1>
        <p>
          El examen que le aplicas a Vera con tus propias preguntas · {esenciales} de {casos.length} casos son
          esenciales
        </p>
      </header>

      {aviso && <p className="mensaje-ok">{aviso}</p>}
      {error && <p className="mensaje-error">{error}</p>}

      <section className="tarjeta formulario">
        <h2>Correr el examen</h2>
        <p className="tenue">
          El conjunto esencial son {esenciales} casos y tarda un par de minutos: va despacio a propósito para
          no toparse con el límite del free tier. El completo ({casos.length} casos) necesita clave de pago.
        </p>
        <div className="fila-acciones">
          <button className="boton" disabled={corriendo || esenciales === 0} onClick={() => correr('esencial')}>
            {corriendo ? 'Corriendo…' : 'Correr conjunto esencial'}
          </button>
          <button
            className="boton secundario"
            disabled={corriendo || casos.length === 0}
            onClick={() => {
              if (!confirm('El conjunto completo no cabe en el free tier: la corrida puede cortarse a medias. ¿Seguir?'))
                return
              correr('completo')
            }}
          >
            Correr conjunto completo
          </button>
        </div>
        {corriendo && <p className="tenue">Preguntando caso por caso… no cierres esta página.</p>}
      </section>

      {corridas.length > 1 && (
        <section className="tarjeta formulario">
          <h2>Comparar dos corridas</h2>
          <p className="tenue">Si un cambio de prompt o de índice rompió algo, aquí se ve qué pregunta dejó de salir bien.</p>
          <label>
            Corrida a revisar
            <select value={nueva} onChange={(e) => { setNueva(e.target.value); setBase(''); setComparacion(null) }}>
              <option value="">Elige una…</option>
              {corridas.map((c) => (
                <option key={c.id} value={c.id}>
                  {etiqueta(c)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Comparar contra
            <select value={base} onChange={(e) => setBase(e.target.value)} disabled={!nueva}>
              <option value="">Elige la referencia…</option>
              {referencias.map((c) => (
                <option key={c.id} value={c.id}>
                  {etiqueta(c)}
                </option>
              ))}
            </select>
          </label>
          <button className="boton" disabled={!nueva || !base} onClick={comparar}>
            Comparar
          </button>

          {comparacion && (
            <div className="trozos">
              <p className={`estado ${VEREDICTOS[comparacion.veredicto] ?? 'aviso'}`}>
                <span className="punto" />
                {comparacion.veredicto} · {comparacion.deltaPuntaje}
              </p>
              <p className="tenue">
                {comparacion.empeoraron} empeoraron · {comparacion.mejoraron} mejoraron ·{' '}
                {comparacion.iguales} sin cambio
              </p>
              {comparacion.cambios.length === 0 && <p className="tenue">Ni una sola respuesta cambió de veredicto.</p>}
              {comparacion.cambios.map((c, i) => (
                <div key={i} className="trozo">
                  <span className="tenue">
                    <span className={`insignia ${NOTAS[c.antes] ?? 'aviso'}`}>{c.antes ?? '—'}</span>
                    {' → '}
                    <span className={`insignia ${NOTAS[c.despues] ?? 'aviso'}`}>{c.despues ?? '—'}</span>
                  </span>
                  <p>{c.pregunta}</p>
                  {c.motivo && <span className="tenue">{c.motivo}</span>}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      <section className="tarjeta listado">
        <h2>Historial</h2>

        {corridas.length === 0 && (
          <p className="tenue">Todavía no has corrido el examen. El primero es la referencia de todo lo demás.</p>
        )}

        {corridas.map((c) => (
          <article key={c.id} className="fila">
            <div className="fila-datos">
              <div>
                <strong>{c.puntaje}</strong>
                <span className="insignia">{c.conjunto}</span>
                <span className={`insignia ${c.completa ? 'ok' : 'error'}`}>{estado(c)}</span>
              </div>
              <p className="tenue">
                Recuperación {c.recuperacion} · Fidelidad {c.fidelidad} · Abstención {c.abstencion}
              </p>
              <p className="tenue">
                {c.total} {c.total === 1 ? 'caso' : 'casos'} · {c.modelo ?? 'sin modelo'} ·{' '}
                {c.tokens.toLocaleString('es-MX')} tokens · {fecha(c.fecha)}
              </p>
            </div>

            <div className="fila-acciones">
              <button className="boton-texto" onClick={() => verCasos(c)}>
                {abierta === c.id ? 'Ocultar casos' : 'Ver casos'}
              </button>
            </div>

            {abierta === c.id && (
              <div className="trozos">
                {detalle.length === 0 && <p className="tenue">Cargando casos…</p>}
                {detalle.map((d, i) => (
                  <div key={i} className="trozo">
                    <span className="tenue">
                      {d.debeSaber ? 'debe saber' : 'debe callarse'}
                      {d.fuenteEsperada && ` · esperaba ${d.fuenteEsperada}`}
                      {d.recuperacionOk === false && ' · no trajo la fuente'}
                      {d.fidelidadOk === false && ' · no dijo lo esperado'}
                    </span>
                    <p>{d.pregunta}</p>
                    <p className="tenue">{d.obtenida ?? '—'}</p>
                    {d.motivo && <span className="tenue">{d.motivo}</span>}
                  </div>
                ))}
              </div>
            )}
          </article>
        ))}
      </section>
    </>
  )
}
