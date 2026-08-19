import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El backend se llama por /api, y el servidor de desarrollo lo reenvía a Spring. Así el
// navegador cree que todo sale del mismo sitio y no hace falta tocar CORS en el backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // El backend suele estar en el 8080. Si lo levantas en otro puerto:
        //   API_URL=http://localhost:8081 npm run dev
        target: process.env.API_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
