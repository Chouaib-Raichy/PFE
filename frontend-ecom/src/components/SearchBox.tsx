import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import type { SuggestItem } from '../types'
import { Icon } from '../ui/Icon'
import { ProductImage } from '../ui/ProductImage'

interface SearchBoxProps {
  onNavigate?: () => void
  autoFocus?: boolean
  className?: string
}

/**
 * Champ de recherche avec suggestions (debounce 250 ms, annulation des requetes
 * obsoletes, navigation clavier, etats vide/chargement). Les suggestions
 * proviennent de /api/products/suggest (Elasticsearch) ; en cas
 * d'indisponibilite le serveur renvoie une liste vide sans erreur.
 */
export function SearchBox({ onNavigate, autoFocus, className }: SearchBoxProps) {
  const [value, setValue] = useState('')
  const [items, setItems] = useState<SuggestItem[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [active, setActive] = useState(-1)
  const navigate = useNavigate()
  const listId = useId()
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const term = value.trim()
    if (!term) {
      setItems([])
      setOpen(false)
      setLoading(false)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    const timer = window.setTimeout(() => {
      api
        .suggestProducts(term, 6, controller.signal)
        .then((res) => {
          setItems(res.suggestions)
          setActive(-1)
          setOpen(true)
        })
        .catch(() => {
          /* requete annulee ou recherche indisponible : aucune liste affichee */
        })
        .finally(() => setLoading(false))
    }, 250)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [value])

  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [])

  const goSearch = () => {
    const term = value.trim()
    setOpen(false)
    onNavigate?.()
    navigate(term ? `/search?q=${encodeURIComponent(term)}` : '/catalog')
  }

  const selectItem = (item: SuggestItem) => {
    setOpen(false)
    setValue('')
    onNavigate?.()
    navigate(`/products/${item.id}`)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((i) => Math.min(i + 1, items.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => Math.max(i - 1, -1))
    } else if (e.key === 'Escape') {
      setOpen(false)
      setActive(-1)
    } else if (e.key === 'Enter' && open && active >= 0 && items[active]) {
      e.preventDefault()
      selectItem(items[active])
    }
  }

  const showList = open && value.trim().length > 0

  return (
    <div className={`search-box${className ? ` ${className}` : ''}`} ref={boxRef}>
      <form
        className="search-input"
        role="search"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          goSearch()
        }}
      >
        <span className="icon">
          <Icon name="search" size={16} />
        </span>
        <input
          className="input"
          type="search"
          placeholder="Rechercher un produit…"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={onKeyDown}
          onFocus={() => {
            if (value.trim()) setOpen(true)
          }}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
          aria-label="Rechercher un produit"
          autoComplete="off"
          autoFocus={autoFocus}
        />
      </form>

      {showList && (
        <div className="search-suggest" role="listbox" id={listId} aria-label="Suggestions de recherche">
          {loading && items.length === 0 && <div className="search-suggest-empty">Recherche…</div>}
          {!loading && items.length === 0 && (
            <div className="search-suggest-empty">Aucune suggestion</div>
          )}
          {items.map((item, i) => (
            <button
              key={item.id}
              id={`${listId}-${i}`}
              type="button"
              role="option"
              aria-selected={i === active}
              className={`search-suggest-item${i === active ? ' is-active' : ''}`}
              onMouseDown={(e) => e.preventDefault()}
              onMouseEnter={() => setActive(i)}
              onClick={() => selectItem(item)}
            >
              <span className="search-suggest-thumb">
                <ProductImage src={item.imageUrl} alt="" iconSize={16} />
              </span>
              <span className="search-suggest-name">{item.name}</span>
              {item.categoryName && (
                <span className="search-suggest-cat">{item.categoryName}</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
