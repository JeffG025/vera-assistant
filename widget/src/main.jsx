import { createRoot } from 'react-dom/client'
import Widget from './Widget.jsx'

// En el build IIFE currentScript existe; en desarrollo (type=module) es null y hay que buscar la etiqueta.
const script =
  document.currentScript ?? document.querySelector('script[data-vera-key]')
const clave = script?.dataset?.veraKey ?? null

// El tema tambien viaja en la etiqueta. Si esperara a la peticion de configuracion, que solo se
// hace al abrir, el negocio veria su burbuja del morado generico hasta que alguien la pulsara
// justo en lo unico que se ve siempre. El panel genera el fragmento ya con estos valores, y lo
// que llegue del servidor manda en cuanto llega.
const tema = {
  color: /^#[0-9a-f]{6}$/i.test(script?.dataset?.veraColor ?? '') ? script.dataset.veraColor : null,
  avatar: script?.dataset?.veraAvatar || null,
  posicion: script?.dataset?.veraPosicion === 'izquierda' ? 'izquierda' : null,
}

// La API vive donde vive este script: el cliente pega una etiqueta y ya, sin configurar una URL
// que se le quede vieja. data-vera-api es solo para desarrollo, donde el widget se sirve aparte.
function dondeEstaLaApi() {
  const explicita = script?.dataset?.veraApi
  if (explicita) return explicita.replace(/\/+$/, '')
  try {
    return new URL(script.src, location.href).origin
  } catch {
    return location.origin
  }
}

// Se monta solo y dentro de un Shadow DOM: la pagina anfitriona no escribe ni un div, su CSS no
// entra y el nuestro no sale. El !important es contra las hojas de estilo que ponen reglas
// globales con !important sobre todos los div.
function montar() {
  if (document.getElementById('vera-widget')) return

  const anfitrion = document.createElement('div')
  anfitrion.id = 'vera-widget'
  anfitrion.style.setProperty('all', 'initial', 'important')
  document.body.appendChild(anfitrion)

  const raiz = anfitrion.attachShadow({ mode: 'open' })
  createRoot(raiz).render(<Widget clave={clave} api={dondeEstaLaApi()} tema={tema} />)
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', montar)
} else {
  montar()
}
