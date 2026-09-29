import 'vite/modulepreload-polyfill'
import { createRoot, hydrateRoot } from 'react-dom/client'
import { createInertiaApp } from '@inertiajs/react'
import { resolvePage } from './resolvePage'

createInertiaApp({
  resolve: resolvePage,
  setup({ el, App, props }) {
    // Pages fall back to client-side rendering when the SSR server is unavailable.
    if (el.hasAttribute('data-server-rendered')) {
      hydrateRoot(el, <App {...props} />)
      return
    }

    createRoot(el).render(<App {...props} />)
  },
})
