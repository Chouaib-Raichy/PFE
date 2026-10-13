/**
 * Panier invite : persiste cote client (localStorage), jamais de donnee
 * sensible (aucun jeton, aucun identifiant client). Sert uniquement a
 * conserver une selection avant connexion ; il est fusionne avec le panier
 * serveur apres authentification puis vide.
 */
const STORAGE_KEY = 'pfeecom.guest-cart.v1'

export interface GuestCartItem {
  productId: number
  productName: string
  unitPrice: number
  imageUrl: string | null
  quantity: number
}

function isValid(item: unknown): item is GuestCartItem {
  if (typeof item !== 'object' || item === null) return false
  const it = item as Record<string, unknown>
  return (
    typeof it.productId === 'number' &&
    typeof it.productName === 'string' &&
    typeof it.unitPrice === 'number' &&
    (it.imageUrl === null || typeof it.imageUrl === 'string') &&
    typeof it.quantity === 'number' &&
    it.quantity > 0
  )
}

/** Lecture defensive : un contenu corrompu ne casse jamais l'application. */
export function loadGuestCart(): GuestCartItem[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isValid)
  } catch {
    return []
  }
}

export function saveGuestCart(items: GuestCartItem[]): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  } catch {
    /* stockage indisponible (mode prive) : le panier reste en memoire */
  }
}

export function clearGuestCart(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}
