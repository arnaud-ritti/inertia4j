import type { ComponentType } from 'react'

const pages = import.meta.glob<{ default: ComponentType<any> }>('./pages/**/*.tsx', { eager: true })

export function resolvePage(name: string) {
  const page = pages[`./pages/${name}.tsx`]

  if (!page) {
    throw new Error(`Unknown page: ${name}`)
  }

  return page
}
