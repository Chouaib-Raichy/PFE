import type { ReactNode } from 'react'

type Tone = 'neutral' | 'gold' | 'success' | 'danger'

export function Badge({
  tone = 'neutral',
  children,
  className = '',
}: {
  tone?: Tone
  children: ReactNode
  className?: string
}) {
  return (
    <span className={`badge${tone !== 'neutral' ? ` badge-${tone}` : ''} ${className}`.trim()}>
      {children}
    </span>
  )
}
