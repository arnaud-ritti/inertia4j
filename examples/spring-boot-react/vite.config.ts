import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'

const hotFile = 'vite.hot'

function inertia4jHotFile(): Plugin {
  return {
    name: 'inertia4j-hot-file',
    apply: 'serve',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        fs.writeFileSync(hotFile, server.resolvedUrls!.local[0])
      })
      const clean = () => fs.rmSync(hotFile, { force: true })
      process.on('exit', clean)
      process.on('SIGINT', () => process.exit())
      process.on('SIGTERM', () => process.exit())
    },
  }
}

export default defineConfig(({ command }) => ({
  plugins: [react(), inertia4jHotFile()],
  base: command === 'build' ? '/build/' : '/',
  build: {
    manifest: true,
    outDir: 'src/main/resources/static/build',
    emptyOutDir: true,
    rollupOptions: { input: 'src/main/frontend/main.tsx' },
  },
  server: {
    origin: 'http://localhost:5173',
    cors: { origin: 'http://localhost:8080' },
  },
}))
