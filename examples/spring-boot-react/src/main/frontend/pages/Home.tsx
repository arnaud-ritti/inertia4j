import { Link } from '@inertiajs/react'

type HomeProps = {
  message: string
}

export default function Home({ message }: HomeProps) {
  return (
    <main>
      <h1>{message}</h1>
      <Link href="/about">About</Link>
    </main>
  )
}
