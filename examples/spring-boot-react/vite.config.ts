import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import inertia from '@inertiajs/vite'
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

export default defineConfig(({ command, isSsrBuild }) => ({
  plugins: [
    react(),
    inertia({ ssr: { entry: 'src/main/frontend/ssr.tsx' } }),
    inertia4jHotFile(),
  ],
  base: command === 'build' ? '/build/' : '/',
  publicDir: false,
  // The SSR bundle is run by Node.js next to the application, so it stays out of the served static directory.
  build: isSsrBuild
    ? {
        outDir: 'build/ssr',
        emptyOutDir: true,
      }
    : {
        manifest: true,
        outDir: 'src/main/resources/static/build',
        emptyOutDir: true,
        rollupOptions: { input: 'src/main/frontend/main.tsx' },
      },
  // Bound to IPv4 so the hot file URL is reachable from the JVM, which resolves localhost to 127.0.0.1.
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    origin: 'http://127.0.0.1:5173',
    cors: { origin: ['http://localhost:8080', 'http://127.0.0.1:8080'] },
  },
}))
