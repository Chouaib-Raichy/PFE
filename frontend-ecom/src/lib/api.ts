import { getAccessToken, refreshAccessToken } from './auth'
import type {
  ApiErrorBody,
  Cart,
  Category,
  CategoryInput,
  Customer,
  CustomerInput,
  Order,
  OrderInput,
  Page,
  Product,
  ProductInput,
  SearchPage,
  SuggestResponse,
} from '../types'

/** Prefixe : en dev Vite proxifie /api vers le gateway :8090, en Docker Nginx fait de meme. */
const BASE = '/api'

export class ApiError extends Error {
  code: string
  status: number
  details?: Record<string, string>

  constructor(message: string, code: string, status: number, details?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

function send(path: string, token: string, init?: RequestInit): Promise<Response> {
  return fetch(BASE + path, {
    ...init,
    headers: {
      ...(init?.body && !(init.body instanceof FormData)
        ? { 'Content-Type': 'application/json' }
        : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })
}

async function authedFetch(path: string, init?: RequestInit): Promise<Response> {
  let token = ''
  try {
    token = await getAccessToken()
  } catch {
    /* non connecté : la requête publique peut encore aboutir */
  }
  const res = await send(path, token, init)
  if (res.status !== 401) return res
  // Jeton refusé : force un rafraîchissement puis réessaie une seule fois.
  try {
    return await send(path, await refreshAccessToken(), init)
  } catch {
    return res
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await authedFetch(path, init)
  } catch {
    throw new ApiError(
      "Le serveur est injoignable. Vérifiez que la plateforme est démarrée.",
      'NETWORK_ERROR',
      0,
    )
  }
  if (!res.ok) {
    let body: ApiErrorBody | undefined
    try {
      body = (await res.json()) as ApiErrorBody
    } catch {
      /* corps non JSON */
    }
    throw new ApiError(
      body?.message ?? `Erreur ${res.status}`,
      body?.code ?? 'HTTP_ERROR',
      res.status,
      body?.details,
    )
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const api = {
  // --- Catalogue ---
  listCategories(): Promise<Category[]> {
    return request<Category[]>('/categories')
  },

  createCategory(input: CategoryInput): Promise<Category> {
    return request<Category>('/categories', { method: 'POST', body: JSON.stringify(input) })
  },

  updateCategory(id: number, input: CategoryInput): Promise<Category> {
    return request<Category>(`/categories/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },

  deleteCategory(id: number): Promise<void> {
    return request<void>(`/categories/${id}`, { method: 'DELETE' })
  },

  listProducts(page = 0, size = 100): Promise<Page<Product>> {
    return request<Page<Product>>(`/products?page=${page}&size=${size}`)
  },

  getProduct(id: number): Promise<Product> {
    return request<Product>(`/products/${id}`)
  },

  createProduct(input: ProductInput): Promise<Product> {
    return request<Product>('/products', { method: 'POST', body: JSON.stringify(input) })
  },

  updateProduct(id: number, input: ProductInput): Promise<Product> {
    return request<Product>(`/products/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },

  deleteProduct(id: number): Promise<void> {
    return request<void>(`/products/${id}`, { method: 'DELETE' })
  },

  /** Televerse (ou remplace) l'image d'un produit ; `onProgress` recoit 0..100. */
  uploadProductImage(id: number, file: File, onProgress?: (pct: number) => void): Promise<Product> {
    return new Promise<Product>((resolve, reject) => {
      void (async () => {
        let token = ''
        try {
          token = await getAccessToken()
        } catch {
          /* non connecte : la reponse sera 401, remontee telle quelle */
        }
        const xhr = new XMLHttpRequest()
        xhr.open('POST', `${BASE}/products/${id}/image`)
        if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
        xhr.upload.onprogress = (event) => {
          if (event.lengthComputable && onProgress) {
            onProgress(Math.round((event.loaded / event.total) * 100))
          }
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              resolve(JSON.parse(xhr.responseText) as Product)
            } catch {
              reject(new ApiError('Reponse serveur illisible.', 'PARSE_ERROR', xhr.status))
            }
            return
          }
          let body: ApiErrorBody | undefined
          try {
            body = JSON.parse(xhr.responseText) as ApiErrorBody
          } catch {
            /* corps non JSON */
          }
          reject(
            new ApiError(
              body?.message ?? `Erreur ${xhr.status}`,
              body?.code ?? 'HTTP_ERROR',
              xhr.status,
              body?.details,
            ),
          )
        }
        xhr.onerror = () =>
          reject(
            new ApiError(
              'Le serveur est injoignable. Verifiez que la plateforme est demarree.',
              'NETWORK_ERROR',
              0,
            ),
          )
        const form = new FormData()
        form.append('file', file)
        xhr.send(form)
      })()
    })
  },

  deleteProductImage(id: number): Promise<void> {
    return request<void>(`/products/${id}/image`, { method: 'DELETE' })
  },

  // --- Recherche (Elasticsearch, repli PostgreSQL cote serveur) ---
  searchProducts(
    q: string,
    opts: { categoryId?: number; page?: number; size?: number; signal?: AbortSignal } = {},
  ): Promise<SearchPage> {
    const params = new URLSearchParams()
    const term = q.trim()
    if (term) params.set('q', term)
    if (opts.categoryId) params.set('categoryId', String(opts.categoryId))
    params.set('page', String(opts.page ?? 0))
    params.set('size', String(opts.size ?? 12))
    return request<SearchPage>(`/products/search?${params.toString()}`, { signal: opts.signal })
  },

  suggestProducts(q: string, limit = 6, signal?: AbortSignal): Promise<SuggestResponse> {
    return request<SuggestResponse>(
      `/products/suggest?q=${encodeURIComponent(q)}&limit=${limit}`,
      { signal },
    )
  },

  // --- Clients ---
  listCustomers(page = 0, size = 100): Promise<Page<Customer>> {
    return request<Page<Customer>>(`/customers?page=${page}&size=${size}`)
  },

  getCustomer(id: number): Promise<Customer> {
    return request<Customer>(`/customers/${id}`)
  },

  /** Provisionne (ou recupere) le profil du client connecte. Idempotent ; aucune
   *  identite n'est envoyee : le serveur deduit le profil du jeton (claim sub). */
  provisionCustomer(): Promise<Customer> {
    return request<Customer>('/customers/me', { method: 'POST' })
  },

  createCustomer(input: CustomerInput): Promise<Customer> {
    return request<Customer>('/customers', { method: 'POST', body: JSON.stringify(input) })
  },

  updateCustomer(id: number, input: CustomerInput): Promise<Customer> {
    return request<Customer>(`/customers/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },

  deleteCustomer(id: number): Promise<void> {
    return request<void>(`/customers/${id}`, { method: 'DELETE' })
  },

  // --- Panier ---
  async getCart(customerId: number): Promise<Cart> {
    try {
      return await request<Cart>(`/carts/${customerId}`)
    } catch (err) {
      // Un panier inexistant est un panier vide, pas une erreur.
      if (err instanceof ApiError && err.status === 404) {
        return { customerId, items: [], total: 0 }
      }
      throw err
    }
  },

  addCartItem(customerId: number, productId: number, quantity: number): Promise<Cart> {
    return request<Cart>(`/carts/${customerId}/items`, {
      method: 'POST',
      body: JSON.stringify({ productId, quantity }),
    })
  },

  removeCartItem(customerId: number, itemId: number): Promise<Cart> {
    return request<Cart>(`/carts/${customerId}/items/${itemId}`, { method: 'DELETE' })
  },

  clearCart(customerId: number): Promise<void> {
    return request<void>(`/carts/${customerId}`, { method: 'DELETE' })
  },

  /** Fusionne un panier invite (cote client) dans le panier serveur, de
   *  facon idempotente (rejouable sans doublon). */
  mergeCart(
    customerId: number,
    items: { productId: number; quantity: number }[],
  ): Promise<Cart> {
    return request<Cart>(`/carts/${customerId}/merge`, {
      method: 'POST',
      body: JSON.stringify({ items }),
    })
  },

  // --- Commandes ---
  createOrder(input: OrderInput): Promise<Order> {
    return request<Order>('/orders', { method: 'POST', body: JSON.stringify(input) })
  },

  listOrders(page = 0, size = 50): Promise<Page<Order>> {
    return request<Page<Order>>(`/orders?page=${page}&size=${size}`)
  },

  getOrder(id: number): Promise<Order> {
    return request<Order>(`/orders/${id}`)
  },
}
