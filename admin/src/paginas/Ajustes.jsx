import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/cliente.js'
import { useAuth } from '../auth/AuthContext.jsx'

// Marcador: la URL de verdad sale cuando Vera esté desplegada (Bloque 8).
const ORIGEN_DEL_WIDGET = 'https://tu-dominio'

const CAMPOS_DEL_NEGOCIO = ['nombre', 'dominiosPermitidos']
const CAMPOS_DEL_ASISTENTE = [
  'asistenteNombre',
  'asistenteSaludo',
  'asistenteIdioma',
  'asistenteColor',
  'asistenteAvatar',
  'asistentePosicion',
]
const CAMPOS_DE_CONTACTOS = ['capturarContactos', 'webhookUrl']

export default function Ajustes() {
  const { usuario, actualizarNegocio } = useAuth()
  const esDuenio = usuario?.rol === 'OWNER'

  const [datos, setDatos] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(null)
  const [copiado, setCopiado] = useState(false)

  useEffect(() => {
    api.negocio().then(setDatos).catch((e) => setError(e.message))
  }, [])

  // El fragmento lleva el tema puesto: sin eso, la burbuja se vería del morado genérico hasta que
  // un visitante la abriera, que es cuando el widget pide la configuración.
  const fragmento = useMemo(() => {
    if (!datos) return ''
    const atributos = [
      `src="${ORIGEN_DEL_WIDGET}/vera.js"`,
      `data-vera-key="${datos.clavePublica}"`,
      `data-vera-color="${datos.asistenteColor}"`,
      `data-vera-avatar="${datos.asistenteAvatar}"`,
    ]
    if (datos.asistentePosicion === 'izquierda') atributos.push('data-vera-posicion="izquierda"')
    return `<script ${atributos.join(' ')}></script>`
  }, [datos])

  if (error && !datos) return <p className="mensaje-error">{error}</p>
  if (!datos) return <p className="tenue">Cargando…</p>

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })
  const sinDominios = !datos.dominiosPermitidos?.trim()

  // Cada tarjeta guarda solo lo suyo: así un error de validación señala el formulario correcto.
  function guardarSolo(campos, cual) {
    return async (e) => {
      e.preventDefault()
      setGuardando(cual)
      setAviso(null)
      setError(null)
      try {
        const nuevo = await api.actualizarNegocio(
          Object.fromEntries(campos.map((c) => [c, datos[c] ?? ''])),
        )
        setDatos(nuevo)
        actualizarNegocio({ ...nuevo })
        setAviso('Cambios guardados')
      } catch (e) {
        setError(e.message)
      } finally {
        setGuardando(null)
      }
    }
  }

  async function rotarClave() {
    if (!confirm('El script instalado en tu sitio dejará de funcionar hasta que lo reemplaces. ¿Continuar?')) return
    try {
      const { clavePublica } = await api.regenerarClave()
      const nuevo = { ...datos, clavePublica }
      setDatos(nuevo)
      actualizarNegocio(nuevo)
      setAviso('Clave regenerada: actualiza el script en tu sitio')
    } catch (e) {
      setError(e.message)
    }
  }

  async function copiar() {
    try {
      await navigator.clipboard.writeText(fragmento)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch {
      setError('El navegador no dejó copiar; selecciona el texto a mano.')
    }
  }

  return (
    <>
      <header className="encabezado">
        <h1>Ajustes</h1>
        <p>Datos del negocio, apariencia del asistente e instalación del widget</p>
      </header>

      {aviso && <p className="mensaje-ok">{aviso}</p>}
      {error && <p className="mensaje-error">{error}</p>}

      <div className="rejilla">
        <form className="tarjeta formulario" onSubmit={guardarSolo(CAMPOS_DEL_NEGOCIO, 'negocio')}>
          <h2>Negocio</h2>

          <label>
            Nombre
            <input name="nombre" value={datos.nombre} onChange={cambiar} disabled={!esDuenio} required />
          </label>

          <label>
            Identificador
            <input value={datos.slug} disabled />
            <span className="tenue">Se generó al crear el negocio y no cambia</span>
          </label>

          <label>
            Dominios permitidos
            <input
              name="dominiosPermitidos"
              value={datos.dominiosPermitidos ?? ''}
              onChange={cambiar}
              disabled={!esDuenio}
              placeholder="mitienda.com, *.mitienda.com"
            />
            <span className="tenue">
              Separados por comas. El comodín <code>*.mitienda.com</code> cubre los subdominios y
              también el dominio pelado. <code>localhost</code> funciona siempre, para que puedas probar.
            </span>
          </label>

          {sinDominios && (
            <p className="mensaje-aviso">
              Tu asistente no responderá en ningún sitio público hasta que añadas aquí el dominio de
              tu web. Es lo que impide que otro copie tu script y gaste tu cuota.
            </p>
          )}

          {esDuenio && (
            <button className="boton" disabled={guardando === 'negocio'}>
              {guardando === 'negocio' ? 'Guardando…' : 'Guardar'}
            </button>
          )}
          {!esDuenio && <p className="tenue">Solo el dueño del negocio puede editar estos datos.</p>}
        </form>

        <form
          className="tarjeta formulario"
          onSubmit={guardarSolo(CAMPOS_DEL_ASISTENTE, 'asistente')}
        >
          <h2>Tu asistente</h2>

          <div className="previsualizacion">
            <span className="burbuja" style={{ background: datos.asistenteColor }}>
              {datos.asistenteAvatar}
            </span>
            <span className="tenue">
              Así se verá en tu sitio,{' '}
              {datos.asistentePosicion === 'izquierda' ? 'abajo a la izquierda' : 'abajo a la derecha'}.
            </span>
          </div>

          <label>
            Nombre del asistente
            <input
              name="asistenteNombre"
              value={datos.asistenteNombre}
              onChange={cambiar}
              disabled={!esDuenio}
              maxLength={60}
            />
          </label>

          <label>
            Saludo
            <input
              name="asistenteSaludo"
              value={datos.asistenteSaludo ?? ''}
              onChange={cambiar}
              disabled={!esDuenio}
              maxLength={500}
              placeholder={`Hola, soy ${datos.asistenteNombre}. ¿En qué te ayudo?`}
            />
            <span className="tenue">Vacío = se arma solo con el nombre del asistente</span>
          </label>

          <div className="campos-en-fila">
            <label>
              Color
              <input
                type="color"
                name="asistenteColor"
                value={datos.asistenteColor}
                onChange={cambiar}
                disabled={!esDuenio}
              />
              <span className="tenue">{datos.asistenteColor}</span>
            </label>

            <label>
              Ícono
              <input
                name="asistenteAvatar"
                value={datos.asistenteAvatar}
                onChange={cambiar}
                disabled={!esDuenio}
                maxLength={8}
              />
              <span className="tenue">Un emoji</span>
            </label>
          </div>

          <div className="campos-en-fila">
            <label>
              Posición
              <select
                name="asistentePosicion"
                value={datos.asistentePosicion}
                onChange={cambiar}
                disabled={!esDuenio}
              >
                <option value="derecha">Abajo a la derecha</option>
                <option value="izquierda">Abajo a la izquierda</option>
              </select>
            </label>

            <label>
              Idioma
              <input
                name="asistenteIdioma"
                value={datos.asistenteIdioma}
                onChange={cambiar}
                disabled={!esDuenio}
                maxLength={20}
              />
            </label>
          </div>

          {esDuenio && (
            <button className="boton" disabled={guardando === 'asistente'}>
              {guardando === 'asistente' ? 'Guardando…' : 'Guardar'}
            </button>
          )}
        </form>

        <form className="tarjeta formulario" onSubmit={guardarSolo(CAMPOS_DE_CONTACTOS, 'contactos')}>
          <h2>Captura de contactos</h2>

          <label className="casilla">
            <input
              type="checkbox"
              name="capturarContactos"
              checked={datos.capturarContactos}
              onChange={(e) => setDatos({ ...datos, capturarContactos: e.target.checked })}
              disabled={!esDuenio}
            />
            Pedir nombre y correo cuando Vera no sepa responder
          </label>

          <p className="tenue">
            Es el único momento en que vale la pena pedirlos: hay una pregunta que tú sí sabes
            contestar y alguien esperándola. Antes sería un peaje; después, tarde.
          </p>

          <label>
            Aviso a tu CRM (webhook)
            <input
              name="webhookUrl"
              value={datos.webhookUrl ?? ''}
              onChange={cambiar}
              disabled={!esDuenio}
              placeholder="https://tu-crm.com/hooks/vera"
              maxLength={500}
            />
            <span className="tenue">
              Vera hace un POST con los datos en cuanto entra uno. Vacío = solo se guardan aquí y
              los descargas en CSV. No se aceptan direcciones internas ni locales.
            </span>
          </label>

          {esDuenio && (
            <button className="boton" disabled={guardando === 'contactos'}>
              {guardando === 'contactos' ? 'Guardando…' : 'Guardar'}
            </button>
          )}
        </form>

        <section className="tarjeta">
          <h2>Instalación del widget</h2>
          <p className="tenue">Pega esto antes de cerrar el &lt;body&gt; de tu sitio.</p>
          <pre className="fragmento">{fragmento}</pre>

          <div className="fila-acciones">
            <button type="button" className="boton" onClick={copiar}>
              {copiado ? 'Copiado' : 'Copiar'}
            </button>
          </div>

          <p className="tenue">
            El color y el ícono viajan en la etiqueta para que tu burbuja se vea tuya desde el primer
            instante, sin esperar a que alguien la abra. Si los cambias aquí arriba, el widget se
            actualiza solo: no hace falta volver a pegar el script.
          </p>

          <dl className="datos">
            <div>
              <dt>Clave pública</dt>
              <dd>
                <code>{datos.clavePublica}</code>
              </dd>
            </div>
          </dl>

          {esDuenio && (
            <button type="button" className="boton secundario" onClick={rotarClave}>
              Regenerar clave
            </button>
          )}
        </section>
      </div>
    </>
  )
}
