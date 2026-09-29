import 'vite/modulepreload-polyfill'
import type { ComponentType } from 'react'
import { createRoot } from 'react-dom/client'
import { createInertiaApp } from '@inertiajs/react'

const pages = import.meta.glob<{ default: ComponentType<any> }>('./pages/**/*.tsx', { eager: true })

createInertiaApp({
  resolve: (name) => {
    const page = pages[`./pages/${name}.tsx`]

    if (!page) {
      throw new Error(`Unknown page: ${name}`)
    }

    return page
  },
  setup({ el, App, props }) {
    createRoot(el).render(<App {...props} />)
  },
})
