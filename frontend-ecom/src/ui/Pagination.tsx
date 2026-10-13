import { Icon } from './Icon'

interface PaginationProps {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

/** Pagination sobre : première/dernière + fenêtre autour de la page courante. */
export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) return null

  const pages: (number | '…')[] = []
  const push = (p: number | '…') => pages.push(p)
  const window = 1

  push(0)
  for (let p = 1; p < totalPages - 1; p++) {
    if (Math.abs(p - page) <= window) push(p)
    else if (pages[pages.length - 1] !== '…') push('…')
  }
  if (totalPages > 1) push(totalPages - 1)

  return (
    <nav className="pagination" aria-label="Pagination">
      <button className="page-btn" onClick={() => onChange(page - 1)} disabled={page === 0} aria-label="Page précédente">
        <Icon name="chevronLeft" size={16} />
      </button>
      {pages.map((p, i) =>
        p === '…' ? (
          <span key={`gap-${i}`} className="page-btn" style={{ border: 'none', background: 'none' }}>
            …
          </span>
        ) : (
          <button
            key={p}
            className="page-btn"
            aria-current={p === page ? 'page' : undefined}
            onClick={() => onChange(p)}
          >
            {p + 1}
          </button>
        ),
      )}
      <button
        className="page-btn"
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="Page suivante"
      >
        <Icon name="chevronRight" size={16} />
      </button>
    </nav>
  )
}
