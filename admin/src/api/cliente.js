const BASE = '/api'
const LLAVE = 'vera.sesion.v1'

let alPerderSesion = null

export function cuandoExpireLaSesion(fn) {
  alPerderSesion = fn
}

export function leerSesion() {
  try {
    const crudo = localStorage.getItem(LLAVE)
    return crudo ? JSON.parse(crudo) : null
  } catch {
    return null
  }
}

export function guardarSesion(sesion) {
  localStorage.setItem(LLAVE, JSON.stringify(sesion))
}

export function borrarSesion() {
  localStorage.removeItem(LLAVE)
}

async function leerError(respuesta) {
  try {
    const cuerpo = await respuesta.json()
    if (cuerpo?.campos) {
      return Object.entries(cuerpo.campos)
        .map(([campo, msg]) => `${campo}: ${msg}`)
        .join(' · ')
    }
    return cuerpo?.error ?? `Error ${respuesta.status}`
  } catch {
    return `Error ${respuesta.status}`
  }
}

// Un 401 con refresco disponible se reintenta una sola vez; si tampoco pasa, se cierra la sesion.
async function intentarRefrescar(refresco) {
  const r = await fetch(`${BASE}/auth/refresco`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresco }),
  })
  if (!r.ok) return null
  const sesion = await r.json()
  guardarSesion(sesion)
  return sesion
}

export async function peticion(ruta, opciones = {}, reintentar = true) {
  const sesion = leerSesion()
  const cabeceras = { 'Content-Type': 'application/json', ...(opciones.headers ?? {}) }
  if (sesion?.acceso) cabeceras.Authorization = `Bearer ${sesion.acceso}`

  const respuesta = await fetch(BASE + ruta, { ...opciones, headers: cabeceras })

  if (respuesta.status === 401 && reintentar && sesion?.refresco) {
    const renovada = await intentarRefrescar(sesion.refresco)
    if (renovada) return peticion(ruta, opciones, false)
    borrarSesion()
    alPerderSesion?.()
    throw new Error('Tu sesión expiró, entra de nuevo')
  }

  if (!respuesta.ok) throw new Error(await leerError(respuesta))
  if (respuesta.status === 204) return null
  return respuesta.json()
}

export const api = {
  login: (datos) => peticion('/auth/login', { method: 'POST', body: JSON.stringify(datos) }),
  registro: (datos) => peticion('/auth/registro', { method: 'POST', body: JSON.stringify(datos) }),
  negocio: () => peticion('/negocio'),
  actualizarNegocio: (datos) => peticion('/negocio', { method: 'PATCH', body: JSON.stringify(datos) }),
  regenerarClave: () => peticion('/negocio/clave-publica', { method: 'POST' }),
  salud: () => peticion('/health'),
}
