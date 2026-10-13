import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { api, ApiError } from '../lib/api'
import {
  clearGuestCart,
  loadGuestCart,
  saveGuestCart,
  type GuestCartItem,
} from '../lib/guestCart'
import type { Cart, Order } from '../types'
import { useAuth } from './AuthProvider'
import { useToast } from '../ui/Toast'

const EMPTY_CART: Cart = { customerId: 0, items: [], total: 0 }
const GUEST_MAX_QTY = 99

/** Instantane minimal du produit requis pour alimenter un panier invite. */
export interface CartProduct {
  id: number
  name: string
  price: number
  imageUrl: string | null
}

function round(value: number): number {
  return Math.round(value * 100) / 100
}

function toCart(items: GuestCartItem[]): Cart {
  const lines = items.map((i) => ({
    itemId: i.productId,
    productId: i.productId,
    productName: i.productName,
    unitPrice: i.unitPrice,
    quantity: i.quantity,
    subtotal: round(i.unitPrice * i.quantity),
  }))
  return {
    customerId: 0,
    items: lines,
    total: round(lines.reduce((sum, l) => sum + l.subtotal, 0)),
  }
}

interface CartState {
  cart: Cart
  count: number
  loading: boolean
  error: string
  placing: boolean
  /** Vrai si le panier affiche provient du serveur (client authentifie). */
  serverBacked: boolean
  refresh: () => Promise<void>
  addItem: (product: CartProduct, quantity?: number) => Promise<void>
  removeItem: (itemId: number) => Promise<void>
  clear: () => Promise<void>
  placeOrder: () => Promise<Order | null>
}

const CartContext = createContext<CartState | null>(null)

/**
 * Etat panier partage (badge d'en-tete + page panier + ajout produit).
 * Invite : selection locale (localStorage). Authentifie : panier serveur.
 * A la connexion, la selection invite est fusionnee avec le panier serveur
 * puis effacee uniquement apres une fusion confirmee.
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const { customerId, hasCustomer } = useAuth()
  const { notify } = useToast()
  const serverBacked = hasCustomer && customerId !== null
  const [serverCart, setServerCart] = useState<Cart>(EMPTY_CART)
  const [guestItems, setGuestItems] = useState<GuestCartItem[]>(() => loadGuestCart())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [placing, setPlacing] = useState(false)
  const mergedFor = useRef<number | null>(null)

  const refresh = useCallback(async () => {
    if (!serverBacked) {
      setServerCart(EMPTY_CART)
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      setServerCart(await api.getCart(customerId))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }, [serverBacked, customerId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // Fusion invite -> serveur, une seule fois par client, apres connexion.
  useEffect(() => {
    if (!serverBacked || guestItems.length === 0) return
    if (mergedFor.current === customerId) return
    mergedFor.current = customerId
    void (async () => {
      try {
        const merged = await api.mergeCart(
          customerId,
          guestItems.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        )
        setServerCart(merged)
        clearGuestCart()
        setGuestItems([])
        notify('Votre panier a été fusionné avec votre compte.', 'success')
      } catch {
        mergedFor.current = null
        notify(
          "Fusion du panier impossible pour l'instant. Votre sélection est conservée.",
          'error',
        )
      }
    })()
  }, [serverBacked, customerId, guestItems, notify])

  const updateGuest = useCallback((next: GuestCartItem[]) => {
    saveGuestCart(next)
    setGuestItems(next)
  }, [])

  const addItem = useCallback(
    async (product: CartProduct, quantity = 1) => {
      if (quantity <= 0) return
      if (serverBacked) {
        try {
          setServerCart(await api.addCartItem(customerId, product.id, quantity))
        } catch (err) {
          notify((err as Error).message, 'error')
          throw err
        }
        return
      }
      const existing = guestItems.find((i) => i.productId === product.id)
      const base: GuestCartItem = existing ?? {
        productId: product.id,
        productName: product.name,
        unitPrice: product.price,
        imageUrl: product.imageUrl,
        quantity: 0,
      }
      const nextQty = Math.min(GUEST_MAX_QTY, base.quantity + quantity)
      const next = existing
        ? guestItems.map((i) => (i.productId === product.id ? { ...i, quantity: nextQty } : i))
        : [...guestItems, { ...base, quantity: nextQty }]
      updateGuest(next)
    },
    [serverBacked, customerId, guestItems, notify, updateGuest],
  )

  const removeItem = useCallback(
    async (itemId: number) => {
      if (serverBacked) {
        try {
          setServerCart(await api.removeCartItem(customerId, itemId))
        } catch (err) {
          notify((err as Error).message, 'error')
        }
        return
      }
      updateGuest(guestItems.filter((i) => i.productId !== itemId))
    },
    [serverBacked, customerId, guestItems, notify, updateGuest],
  )

  const clear = useCallback(async () => {
    if (serverBacked) {
      try {
        await api.clearCart(customerId)
        setServerCart({ customerId, items: [], total: 0 })
      } catch (err) {
        notify((err as Error).message, 'error')
      }
      return
    }
    updateGuest([])
    clearGuestCart()
  }, [serverBacked, customerId, notify, updateGuest])

  const placeOrder = useCallback(async (): Promise<Order | null> => {
    if (!serverBacked || serverCart.items.length === 0) return null
    setPlacing(true)
    try {
      const order = await api.createOrder({
        customerId,
        paymentMethod: 'CASH_ON_DELIVERY',
        items: serverCart.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      })
      await api.clearCart(customerId)
      setServerCart({ customerId, items: [], total: 0 })
      return order
    } catch (err) {
      notify(err instanceof ApiError ? err.message : 'Commande impossible', 'error')
      return null
    } finally {
      setPlacing(false)
    }
  }, [serverBacked, customerId, serverCart, notify])

  const cart = serverBacked ? serverCart : toCart(guestItems)

  const value = useMemo<CartState>(
    () => ({
      cart,
      count: cart.items.reduce((sum, i) => sum + i.quantity, 0),
      loading,
      error,
      placing,
      serverBacked,
      refresh,
      addItem,
      removeItem,
      clear,
      placeOrder,
    }),
    [cart, loading, error, placing, serverBacked, refresh, addItem, removeItem, clear, placeOrder],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart(): CartState {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart doit etre utilise dans <CartProvider>')
  return ctx
}
