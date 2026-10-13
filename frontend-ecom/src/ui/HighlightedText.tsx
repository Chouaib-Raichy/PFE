import type { ReactNode } from 'react'

const MARK_OPEN = '<em>'
const MARK_CLOSE = '</em>'

/**
 * Rend un extrait Elasticsearch (termes surlignes encadres par `<em>...</em>`)
 * sans jamais utiliser dangerouslySetInnerHTML : on decoupe sur ces deux
 * balises exactes et on rend le texte en noeuds React echappes. Un nom de
 * produit ne peut donc pas injecter de HTML.
 */
export function HighlightedText({ text }: { text: string }) {
  const nodes: ReactNode[] = []
  const segments = text.split(MARK_OPEN)

  segments.forEach((segment, index) => {
    if (index === 0) {
      if (segment) nodes.push(segment)
      return
    }
    const closeAt = segment.indexOf(MARK_CLOSE)
    const marked = closeAt >= 0 ? segment.slice(0, closeAt) : segment
    const rest = closeAt >= 0 ? segment.slice(closeAt + MARK_CLOSE.length) : ''
    nodes.push(<mark key={index}>{marked}</mark>)
    if (rest) nodes.push(rest)
  })

  return <>{nodes}</>
}
