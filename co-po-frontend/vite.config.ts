import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  preview: {
    // Allow the Render host used for preview/production serving
    host: true,
    port: Number(process.env.PORT) || 4173,
    // Add your Render frontend host here so Vite preview accepts requests
    allowedHosts: ['co-po-online-1.onrender.com'],
  },
})
