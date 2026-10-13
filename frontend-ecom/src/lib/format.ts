/**
 * Utilitaires de formatage (prix, dates) cohérents dans toute l'application.
 */

const priceFormat = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
})

export const formatPrice = (value: number): string => priceFormat.format(value)

/** Analyse une date ISO ; renvoie null si absente ou invalide (évite l'effet « 01 janv. 1970 »). */
const parseDate = (iso?: string | null): Date | null => {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

export const formatDate = (iso?: string | null): string => {
  const date = parseDate(iso)
  return date
    ? date.toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: 'long',
        year: 'numeric',
      })
    : 'Date indisponible'
}

export const formatDateTime = (iso?: string | null): string => {
  const date = parseDate(iso)
  return date
    ? date.toLocaleString('fr-FR', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : 'Date indisponible'
}

/** Initiales pour les avatars (ex. « Yassine Alami » -> « YA »). */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
export const isValidEmail = (value: string): boolean => EMAIL_RE.test(value.trim())
