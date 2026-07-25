import { useState } from 'react'

// Estilos en linea a proposito: nada de CSS externo, para que el build sea un unico archivo.
const estilos = {
  raiz: {
    position: 'fixed',
    right: '1.25rem',
    bottom: '1.25rem',
    zIndex: 2147483000,
    fontFamily: "system-ui, -apple-system, 'Segoe UI', sans-serif",
  },
  burbuja: {
    width: '3.5rem',
    height: '3.5rem',
    borderRadius: '50%',
    border: 'none',
    background: '#4f46e5',
    color: '#fff',
    fontSize: '1.5rem',
    cursor: 'pointer',
    boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
  },
  panel: {
    width: 'min(22rem, calc(100vw - 2.5rem))',
    background: '#fff',
    color: '#1f2330',
    border: '1px solid #e3e5ea',
    borderRadius: '0.9rem',
    boxShadow: '0 16px 48px rgba(0,0,0,0.18)',
    overflow: 'hidden',
    marginBottom: '0.75rem',
  },
  cabecera: {
    background: '#4f46e5',
    color: '#fff',
    padding: '0.9rem 1rem',
    fontWeight: 600,
  },
  cuerpo: {
    padding: '1rem',
    fontSize: '0.9rem',
    lineHeight: 1.55,
  },
  clave: {
    marginTop: '0.75rem',
    fontSize: '0.75rem',
    color: '#6b7280',
    wordBreak: 'break-all',
  },
}

export default function Widget({ clave }) {
  const [abierto, setAbierto] = useState(false)

  return (
    <div style={estilos.raiz}>
      {abierto && (
        <div style={estilos.panel}>
          <div style={estilos.cabecera}>Vera</div>
          <div style={estilos.cuerpo}>
            <p style={{ margin: 0 }}>
              Esqueleto del widget. El chat real llega en el bloque 5, sobre el
              motor de respuesta del bloque 3.
            </p>
            <p style={estilos.clave}>
              clave pública: <code>{clave ?? 'no recibida'}</code>
            </p>
          </div>
        </div>
      )}
      <button
        style={estilos.burbuja}
        onClick={() => setAbierto((v) => !v)}
        aria-label={abierto ? 'Cerrar el asistente' : 'Abrir el asistente'}
      >
        {abierto ? '×' : '💬'}
      </button>
    </div>
  )
}
