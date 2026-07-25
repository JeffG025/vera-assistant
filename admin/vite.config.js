import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5177,
    // El panel llama rutas relativas; el proxy evita pelear con CORS en desarrollo.
    proxy: {
      '/api': 'http://localhost:8090',
    },
  },
})
