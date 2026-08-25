import { useEffect, useRef, useState } from 'react'
import { leerEventos } from './sse.js'

// Color de reserva: es el que se ve mientras llega la configuracion del negocio, y el que
// queda si el widget se instala sin panel. El de verdad viaja en --vera-color.
const COLOR_RESERVA = '#4f46e5'
const COLOR = 'var(--vera-color)'

// Dentro del Shadow DOM nada hereda de la pagina, asi que el widget parte de cero: esto es todo
// lo que necesita para verse igual en cualquier sitio. Aqui viven las pseudoclases, que no se
// pueden escribir en linea.
const BASE = `
  *, *::before, *::after { box-sizing: border-box; }
  button, input { font: inherit; color: inherit; }
  button { cursor: pointer; }
  button:disabled { cursor: default; opacity: 0.5; }
  :focus-visible { outline: 2px solid ${COLOR}; outline-offset: 2px; }
  .vera-hilo::-webkit-scrollbar { width: 6px; }
  .vera-hilo::-webkit-scrollbar-thumb { background: #d5d7de; border-radius: 3px; }
  @keyframes vera-latido { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }
  .vera-punto { animation: vera-latido 1.2s infinite; }
  .vera-punto:nth-child(2) { animation-delay: 0.15s; }
  .vera-punto:nth-child(3) { animation-delay: 0.3s; }
  /* Existe solo para el lector de pantalla: fuera de la vista pero dentro del arbol. */
  .vera-oculto {
    position: absolute; width: 1px; height: 1px; margin: -1px;
    padding: 0; border: 0; overflow: hidden; clip-path: inset(50%);
  }
  /* Estas dos pisan estilos en linea, y para eso hace falta !important. */
  @media (max-width: 30rem) {
    .vera-ventana { width: calc(100vw - 1.5rem) !important; }
  }
  @media (prefers-reduced-motion: reduce) {
    .vera-punto { animation: none !important; }
  }
`

// Estilos en linea a proposito: nada de CSS externo, para que el build sea un unico archivo.
const estilos = {
  raiz: {
    position: 'fixed',
    right: '1.25rem',
    bottom: '1.25rem',
    zIndex: 2147483000,
    fontFamily: "system-ui, -apple-system, 'Segoe UI', sans-serif",
    fontSize: '16px',
    lineHeight: 1.5,
    color: '#1f2330',
    textAlign: 'left',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-end',
  },
  burbuja: {
    width: '3.5rem',
    height: '3.5rem',
    borderRadius: '50%',
    border: 'none',
    background: COLOR,
    color: '#fff',
    fontSize: '1.5rem',
    boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
  },
  ventana: {
    display: 'flex',
    flexDirection: 'column',
    width: 'min(23rem, calc(100vw - 2.5rem))',
    height: 'min(32rem, calc(100dvh - 6rem))',
    background: '#fff',
    border: '1px solid #e3e5ea',
    borderRadius: '0.9rem',
    boxShadow: '0 16px 48px rgba(0,0,0,0.18)',
    overflow: 'hidden',
    marginBottom: '0.75rem',
  },
  cabecera: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '0.5rem',
    background: COLOR,
    color: '#fff',
    padding: '0.85rem 1rem',
  },
  cerrar: {
    background: 'transparent',
    border: 'none',
    color: '#fff',
    fontSize: '1.35rem',
    lineHeight: 1,
    padding: '0 0.25rem',
  },
  hilo: {
    flex: 1,
    overflowY: 'auto',
    padding: '1rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.6rem',
    background: '#f7f8fa',
  },
  burbujaMensaje: {
    maxWidth: '85%',
    padding: '0.6rem 0.8rem',
    borderRadius: '0.8rem',
    fontSize: '0.9rem',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
  pie: {
    display: 'flex',
    gap: '0.5rem',
    padding: '0.75rem',
    borderTop: '1px solid #e9eaee',
    background: '#fff',
  },
  entrada: {
    flex: 1,
    minWidth: 0,
    padding: '0.6rem 0.75rem',
    borderRadius: '0.6rem',
    border: '1px solid #d5d7de',
    background: '#fff',
  },
  enviar: {
    border: 'none',
    borderRadius: '0.6rem',
    background: COLOR,
    color: '#fff',
    padding: '0 0.9rem',
    fontWeight: 600,
  },
  votos: {
    display: 'flex',
    gap: '0.3rem',
    marginTop: '0.4rem',
  },
  voto: {
    background: 'transparent',
    border: 'none',
    padding: '0.1rem 0.25rem',
    fontSize: '0.85rem',
    lineHeight: 1,
    opacity: 0.5,
  },
  contacto: {
    marginTop: '0.5rem',
    padding: '0.6rem',
    background: '#f2f3f7',
    border: '1px solid #e3e5ea',
    borderRadius: '0.6rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.35rem',
  },
  contactoTexto: {
    fontSize: '0.8rem',
    margin: 0,
  },
  gracias: {
    marginTop: '0.4rem',
    fontSize: '0.75rem',
    color: '#6b7280',
  },
  reintentar: {
    display: 'block',
    marginTop: '0.45rem',
    background: 'transparent',
    border: '1px solid currentColor',
    borderRadius: '0.4rem',
    padding: '0.2rem 0.55rem',
    fontSize: '0.75rem',
    color: 'inherit',
  },
  citas: {
    marginTop: '0.4rem',
    fontSize: '0.75rem',
    color: '#6b7280',
  },
  botonCitas: {
    background: 'transparent',
    border: 'none',
    padding: 0,
    color: COLOR,
    fontSize: '0.75rem',
    textDecoration: 'underline',
  },
  marca: {
    fontSize: '0.7rem',
    color: '#9ca3af',
    textAlign: 'center',
    padding: '0 0 0.5rem',
    background: '#fff',
    margin: 0,
  },
}

function estiloDe(mensaje) {
  if (mensaje.error) {
    return { ...estilos.burbujaMensaje, alignSelf: 'flex-start', background: '#fdecea', color: '#a03027' }
  }
  return mensaje.rol === 'visitante'
    ? { ...estilos.burbujaMensaje, alignSelf: 'flex-end', background: COLOR, color: '#fff' }
    : { ...estilos.burbujaMensaje, alignSelf: 'flex-start', background: '#fff', border: '1px solid #e9eaee' }
}

// El motor devuelve los 5 fragmentos que recupero, pero el modelo solo cita algunos: mostrar
// los otros seria decirle al visitante que la respuesta salio de textos que nadie uso.
function soloLasCitadas(texto, fuentes) {
  const citados = new Set([...texto.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1])))
  return (fuentes ?? []).filter((f) => citados.has(f.numero))
}

// El teclado en pantalla del movil no encoge la ventana del navegador: la tapa por abajo, que
// es justo donde esta el campo de escribir. visualViewport si reporta lo que queda visible.
function useAltoVisible() {
  const [alto, setAlto] = useState(null)
  useEffect(() => {
    const visible = window.visualViewport
    if (!visible) return
    const medir = () => setAlto(visible.height)
    medir()
    visible.addEventListener('resize', medir)
    return () => visible.removeEventListener('resize', medir)
  }, [])
  return alto
}

export default function Widget({ clave, api, tema = {} }) {
  const [abierto, setAbierto] = useState(false)
  const [config, setConfig] = useState(null)
  const [mensajes, setMensajes] = useState([])
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [citasAbiertas, setCitasAbiertas] = useState(null)
  const [ultimaPregunta, setUltimaPregunta] = useState(null)
  // Cuota diaria agotada: reintentar no arregla nada, asi que se cierra la entrada.
  const [bloqueado, setBloqueado] = useState(false)
  // null = no se ha pedido | enviando | listo | error. Una vez dados, no se vuelven a pedir.
  const [contacto, setContacto] = useState(null)
  const conversacion = useRef(null)
  const hiloRef = useRef(null)
  const entradaRef = useRef(null)
  const burbujaRef = useRef(null)
  // Una medida absurda (una pestana en segundo plano llega a reportar 0) encogeria la ventana
  // hasta hacerla inservible: por debajo de este piso se prefiere el alto normal.
  const altoVisible = useAltoVisible()
  const topeDeAlto = altoVisible > 240 ? Math.round(altoVisible - 88) : null

  // La configuracion se pide al abrir, no al cargar la pagina: el visitante que nunca abre el
  // chat no debe pagar ni una peticion.
  useEffect(() => {
    if (!abierto || config || !clave) return
    fetch(`${api}/api/widget/config`, { headers: { 'X-Vera-Key': clave } })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error('clave publica no valida'))))
      .then((c) => {
        setConfig(c)
        setMensajes([{ rol: 'asistente', texto: c.saludo }])
      })
      .catch(() => setMensajes([{ rol: 'asistente', error: true, texto: 'Este asistente no esta disponible.' }]))
  }, [abierto, config, clave, api])

  useEffect(() => {
    if (abierto) entradaRef.current?.focus()
  }, [abierto, config])

  // Seguir la respuesta mientras se escribe: si no, el visitante ve el hilo quieto.
  useEffect(() => {
    if (hiloRef.current) hiloRef.current.scrollTop = hiloRef.current.scrollHeight
  }, [mensajes])

  // Al cerrar, el foco vuelve a la burbuja: si no, se queda en la nada y el siguiente tabulador
  // arranca desde el principio de la pagina del cliente.
  function cerrar() {
    setAbierto(false)
    burbujaRef.current?.focus()
  }

  function cambiarUltimo(cambio) {
    setMensajes((previos) => {
      const copia = [...previos]
      copia[copia.length - 1] = { ...copia[copia.length - 1], ...cambio }
      return copia
    })
  }

  function preguntar(evento) {
    evento.preventDefault()
    const pregunta = texto.trim()
    if (!pregunta || enviando || !clave || bloqueado) return
    setTexto('')
    setUltimaPregunta(pregunta)
    setMensajes((previos) => [...previos, { rol: 'visitante', texto: pregunta }])
    conversar(pregunta)
  }

  // La pregunta ya esta en el hilo: aqui solo se pide la respuesta. Separarlo deja que el
  // reintento reuse todo esto sin volver a pintar la burbuja del visitante.
  async function conversar(pregunta) {
    setEnviando(true)
    setMensajes((previos) => [...previos, { rol: 'asistente', texto: '', escribiendo: true }])

    try {
      const respuesta = await fetch(`${api}/api/widget/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Vera-Key': clave },
        body: JSON.stringify({ texto: pregunta, conversacionId: conversacion.current }),
      })
      if (!respuesta.ok || !respuesta.body) {
        const cuerpo = await respuesta.json().catch(() => null)
        throw new Error(cuerpo?.error ?? 'El asistente no pudo responder.')
      }

      let completo = ''
      await leerEventos(respuesta.body, (evento, datos) => {
        if (evento === 'pedazo') {
          completo += JSON.parse(datos).texto
          cambiarUltimo({ texto: completo, escribiendo: false })
        } else if (evento === 'fin') {
          const fin = JSON.parse(datos)
          conversacion.current = fin.conversacionId
          cambiarUltimo({
            texto: fin.respuesta ?? completo,
            escribiendo: false,
            mensajeId: fin.mensajeId,
            sinRespuesta: fin.sinInformacion,
            fuentes: soloLasCitadas(fin.respuesta ?? completo, fin.fuentes),
          })
        } else if (evento === 'error') {
          const fallo = JSON.parse(datos)
          if (fallo.reintentable === false) setBloqueado(true)
          cambiarUltimo({
            texto: fallo.error,
            escribiendo: false,
            error: true,
            reintentable: fallo.reintentable !== false,
          })
        }
      })
    } catch (e) {
      // Aqui cae tanto el servidor caido como el visitante sin internet: el mensaje lo dice.
      cambiarUltimo({
        texto: e.message === 'Failed to fetch' ? 'No pude conectarme. Revisa tu conexion.' : e.message,
        escribiendo: false,
        error: true,
        reintentable: true,
      })
    } finally {
      setEnviando(false)
      entradaRef.current?.focus()
    }
  }

  // El voto se pinta antes de que el servidor conteste: es informacion suya, no del visitante,
  // y hacerle esperar por un pulgar seria peor que perder un voto suelto si la red falla.
  function votar(indice, valor) {
    const mensaje = mensajes[indice]
    if (!mensaje?.mensajeId || mensaje.voto) return
    setMensajes((previos) =>
      previos.map((m, i) => (i === indice ? { ...m, voto: valor } : m)),
    )
    fetch(`${api}/api/widget/mensajes/${mensaje.mensajeId}/calificacion`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Vera-Key': clave },
      body: JSON.stringify({ valor }),
    }).catch(() => {})
  }

  // Se piden los datos justo cuando Vera no supo responder: hay una pregunta que el negocio si
  // sabe contestar y alguien esperandola. Pedirlos antes seria un peaje; despues, tarde.
  async function dejarContacto(evento) {
    evento.preventDefault()
    const formulario = evento.target
    const nombre = formulario.nombre.value.trim()
    const correo = formulario.correo.value.trim()
    if (!nombre || !correo) return

    const pregunta = [...mensajes].reverse().find((m) => m.rol === 'visitante')?.texto
    setContacto('enviando')
    try {
      const respuesta = await fetch(`${api}/api/widget/contactos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Vera-Key': clave },
        body: JSON.stringify({
          nombre,
          email: correo,
          pregunta,
          conversacionId: conversacion.current,
        }),
      })
      setContacto(respuesta.ok ? 'listo' : 'error')
    } catch {
      setContacto('error')
    }
  }

  function reintentar() {
    if (!ultimaPregunta || enviando) return
    // Se quita la burbuja del error: el reintento la sustituye en vez de acumularla.
    setMensajes((previos) => previos.slice(0, -1))
    conversar(ultimaPregunta)
  }

  // Un aria-live sobre el hilo entero releeria la respuesta completa con cada pedazo que llega.
  // Este aviso cambia dos veces por turno: al empezar a escribir y al terminar.
  const ultimo = mensajes[mensajes.length - 1]
  const aviso = enviando
    ? 'Escribiendo la respuesta'
    : ultimo?.rol === 'asistente'
      ? ultimo.texto
      : ''

  const nombre = config?.asistente ?? 'Vera'

  // Muchos sitios ya tienen algo pegado abajo a la derecha (carrito, cookies, otro chat), asi
  // que el negocio elige el lado. El color viaja como variable para que valga tambien dentro
  // de la hoja de estilos, donde un objeto en linea no llega.
  const estiloRaiz = {
    ...estilos.raiz,
    '--vera-color': config?.color ?? tema.color ?? COLOR_RESERVA,
    ...((config?.posicion ?? tema.posicion) === 'izquierda'
      ? { right: 'auto', left: '1.25rem', alignItems: 'flex-start' }
      : null),
  }

  return (
    <div style={estiloRaiz} onKeyDown={(e) => e.key === 'Escape' && cerrar()}>
      <style>{BASE}</style>

      {abierto && (
        <div
          style={
            topeDeAlto ? { ...estilos.ventana, maxHeight: `${topeDeAlto}px` } : estilos.ventana
          }
          className="vera-ventana"
          role="dialog"
          aria-label={`Asistente de ${config?.negocio ?? 'este sitio'}`}
        >
          <div style={estilos.cabecera}>
            <div>
              <strong>{nombre}</strong>
              {config?.negocio && (
                <div style={{ fontSize: '0.75rem', opacity: 0.85 }}>{config.negocio}</div>
              )}
            </div>
            <button style={estilos.cerrar} onClick={cerrar} aria-label="Cerrar el asistente">
              ×
            </button>
          </div>

          <div style={estilos.hilo} ref={hiloRef} className="vera-hilo">
            {!clave && (
              <div style={estiloDe({ error: true })}>
                Falta la clave publica en la etiqueta del script.
              </div>
            )}

            {mensajes.map((m, i) => (
              <div key={i} style={{ display: 'contents' }}>
                <div style={estiloDe(m)}>
                  {m.escribiendo ? (
                    <span aria-label="Escribiendo">
                      <span className="vera-punto">●</span>
                      <span className="vera-punto">●</span>
                      <span className="vera-punto">●</span>
                    </span>
                  ) : (
                    m.texto
                  )}

                  {config?.pideContacto && m.sinRespuesta && contacto !== 'listo' && (
                    <form style={estilos.contacto} onSubmit={dejarContacto}>
                      <p style={estilos.contactoTexto}>
                        <strong>¿Te escribimos?</strong> Déjanos tus datos y alguien del equipo te
                        responde esto mismo.
                      </p>
                      <input
                        style={estilos.entrada}
                        name="nombre"
                        placeholder="Tu nombre"
                        aria-label="Tu nombre"
                        maxLength={120}
                        required
                      />
                      <input
                        style={estilos.entrada}
                        name="correo"
                        type="email"
                        placeholder="Tu correo"
                        aria-label="Tu correo"
                        maxLength={180}
                        required
                      />
                      <button style={estilos.enviar} type="submit" disabled={contacto === 'enviando'}>
                        {contacto === 'enviando' ? 'Enviando…' : 'Que me escriban'}
                      </button>
                      {contacto === 'error' && (
                        <p style={{ ...estilos.contactoTexto, color: '#a03027' }}>
                          No pude guardar tus datos. Inténtalo de nuevo.
                        </p>
                      )}
                    </form>
                  )}

                  {config?.pideContacto && m.sinRespuesta && contacto === 'listo' && (
                    <p style={estilos.gracias}>Listo. Te escribimos en cuanto podamos.</p>
                  )}

                  {m.mensajeId && !m.error && !m.escribiendo && (
                    m.voto ? (
                      <p style={estilos.gracias}>Gracias por decírmelo.</p>
                    ) : (
                      <div style={estilos.votos}>
                        <button
                          style={estilos.voto}
                          onClick={() => votar(i, 1)}
                          aria-label="Esta respuesta me sirvió"
                        >
                          👍
                        </button>
                        <button
                          style={estilos.voto}
                          onClick={() => votar(i, -1)}
                          aria-label="Esta respuesta no me sirvió"
                        >
                          👎
                        </button>
                      </div>
                    )
                  )}

                  {m.error && m.reintentable && !enviando && !bloqueado && (
                    <button style={estilos.reintentar} onClick={reintentar}>
                      Reintentar
                    </button>
                  )}

                  {m.fuentes?.length > 0 && (
                    <div style={estilos.citas}>
                      <button
                        style={estilos.botonCitas}
                        onClick={() => setCitasAbiertas(citasAbiertas === i ? null : i)}
                        aria-expanded={citasAbiertas === i}
                      >
                        {citasAbiertas === i ? 'Ocultar fuentes' : `Ver ${m.fuentes.length} fuente${m.fuentes.length > 1 ? 's' : ''}`}
                      </button>
                      {citasAbiertas === i && (
                        <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.1rem' }}>
                          {m.fuentes.map((f) => (
                            <li key={f.numero}>
                              [{f.numero}]{' '}
                              {f.url ? (
                                <a href={f.url} target="_blank" rel="noopener noreferrer" style={{ color: COLOR }}>
                                  {f.titulo ?? f.url}
                                </a>
                              ) : (
                                (f.titulo ?? 'sin titulo')
                              )}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>

          <form style={estilos.pie} onSubmit={preguntar}>
            <input
              ref={entradaRef}
              style={estilos.entrada}
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Escribe tu pregunta…"
              aria-label="Escribe tu pregunta"
              maxLength={1000}
              disabled={!clave || bloqueado}
            />
            <button
              style={estilos.enviar}
              type="submit"
              disabled={!texto.trim() || enviando || bloqueado}
              aria-label="Enviar"
            >
              →
            </button>
          </form>
          <p className="vera-oculto" role="status" aria-live="polite">
            {aviso}
          </p>
          <p style={estilos.marca}>con tecnología de Vera</p>
        </div>
      )}

      <button
        ref={burbujaRef}
        style={estilos.burbuja}
        onClick={() => setAbierto((v) => !v)}
        aria-expanded={abierto}
        aria-label={abierto ? 'Cerrar el asistente' : `Abrir el asistente de ${config?.negocio ?? 'este sitio'}`}
      >
        {abierto ? '×' : (config?.avatar ?? tema.avatar ?? '💬')}
      </button>
    </div>
  )
}
