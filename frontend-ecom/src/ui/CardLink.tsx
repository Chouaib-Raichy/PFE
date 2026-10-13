import { Link } from 'react-router-dom'
import { Icon } from './Icon'

type CardLinkIcon = 'box' | 'tag' | 'users' | 'receipt'

interface CardLinkProps {
  to: string
  title: string
  desc: string
  icon?: CardLinkIcon
}

export function CardLink({ to, title, desc, icon = 'box' }: CardLinkProps) {
  return (
    <Link to={to} className="card card-pad card-hover card-link">
      <span className="stat-icon">
        <Icon name={icon} size={18} />
      </span>
      <div className="stack gap-2">
        <span className="semibold">{title}</span>
        <p className="muted small" style={{ maxWidth: '32ch' }}>
          {desc}
        </p>
      </div>
      <Icon name="chevronRight" size={18} className="muted" style={{ marginLeft: 'auto' }} />
    </Link>
  )
}