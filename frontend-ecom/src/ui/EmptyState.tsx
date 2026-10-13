import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { Button } from './Button'

interface EmptyStateProps {
  icon?: Parameters<typeof Icon>[0]['name']
  title?: string
  message: ReactNode
  actionLabel?: string
  onAction?: () => void
  tone?: 'default' | 'error'
}

export function EmptyState({
  icon = 'box',
  title,
  message,
  actionLabel,
  onAction,
  tone = 'default',
}: EmptyStateProps) {
  return (
    <div className={`state${tone === 'error' ? ' state-error' : ''}`} role={tone === 'error' ? 'alert' : undefined}>
      <span className="state-icon">
        <Icon name={tone === 'error' ? 'alert' : icon} size={44} />
      </span>
      {title && <h3>{title}</h3>}
      <p className="muted">{message}</p>
      {actionLabel && onAction && (
        <Button variant="primary" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  )
}
