/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: vite.config.ts
 * Description: Configures React development and the same-origin API proxy.
 * Project version: 0.1.0
 */

import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Resolves the development backend address and forwards browser API calls through the Vite origin.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'BACKEND_')
  const target = process.env.BACKEND_URL ?? env.BACKEND_URL ?? 'http://127.0.0.1:8080'

  // The browser uses its own origin in development and in the Nginx container.
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api/v1/': { target },
        '/actuator/health/readiness': { target },
      },
    },
  }
})
