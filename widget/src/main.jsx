import { createRoot } from 'react-dom/client'
import Widget from './Widget.jsx'

// En el build IIFE currentScript existe; en desarrollo (type=module) es null y hay que buscar la etiqueta.
const script =
  document.currentScript ?? document.querySelector('script[data-vera-key]')
const clave = script?.dataset?.veraKey ?? null

// Se monta solo: la pagina anfitriona no escribe ni un div.
function montar() {
  const contenedor = document.createElement('div')
  contenedor.id = 'vera-widget'
  document.body.appendChild(contenedor)
  createRoot(contenedor).render(<Widget clave={clave} />)
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', montar)
} else {
  montar()
}
