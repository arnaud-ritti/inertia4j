import { Link } from '@inertiajs/react'

type AboutProps = {
  springBootVersion: string
}

export default function About({ springBootVersion }: AboutProps) {
  return (
    <main>
      <h1>About</h1>
      <p>Served by Spring Boot {springBootVersion} through Inertia4J.</p>
      <Link href="/">Home</Link>
    </main>
  )
}
