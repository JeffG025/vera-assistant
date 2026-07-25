import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { api, borrarSesion, cuandoExpireLaSesion, guardarSesion, leerSesion } from '../api/cliente.js'

const Contexto = createContext(null)

export function ProveedorAuth({ children }) {
  const [sesion, setSesion] = useState(() => leerSesion())

  useEffect(() => {
    // El cliente HTTP avisa cuando el refresco tambien fallo; aqui se refleja en la UI.
    cuandoExpireLaSesion(() => setSesion(null))
  }, [])

  const valor = useMemo(
    () => ({
      sesion,
      usuario: sesion?.usuario ?? null,
      negocio: sesion?.usuario?.negocio ?? null,
      async entrar(datos) {
        const nueva = await api.login(datos)
        guardarSesion(nueva)
        setSesion(nueva)
      },
      async registrarse(datos) {
        const nueva = await api.registro(datos)
        guardarSesion(nueva)
        setSesion(nueva)
      },
      salir() {
        borrarSesion()
        setSesion(null)
      },
      actualizarNegocio(negocio) {
        setSesion((previa) => {
          if (!previa) return previa
          const nueva = { ...previa, usuario: { ...previa.usuario, negocio } }
          guardarSesion(nueva)
          return nueva
        })
      },
    }),
    [sesion],
  )

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

export function useAuth() {
  const contexto = useContext(Contexto)
  if (!contexto) throw new Error('useAuth debe usarse dentro de ProveedorAuth')
  return contexto
}
