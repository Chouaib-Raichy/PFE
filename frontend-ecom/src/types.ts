export interface Category {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface Product {
  id: number
  name: string
  description: string | null
  price: number
  stockQuantity: number
  imageUrl: string | null
  active: boolean
  categoryId: number | null
  categoryName: string | null
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** Resultat de recherche (Elasticsearch) : produit + extraits surlignes. */
export interface ProductSearchHit extends Product {
  highlights?: Record<string, string> | null
}

export interface SearchPage extends Page<ProductSearchHit> {
  /** true = repli PostgreSQL utilise (Elasticsearch indisponible). */
  degraded: boolean
}

export interface SuggestItem {
  id: number
  name: string
  categoryName: string | null
  imageUrl: string | null
}

export interface SuggestResponse {
  suggestions: SuggestItem[]
  degraded: boolean
}

export interface Customer {
  id: number
  firstName: string
  lastName: string
  email: string
  phone: string | null
  createdAt: string
  updatedAt: string
}

export interface CustomerInput {
  firstName: string
  lastName: string
  email: string
  phone?: string
}

export interface CartItem {
  itemId: number
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export interface Cart {
  customerId: number
  items: CartItem[]
  total: number
}

export interface OrderItem {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export type OrderStatus = 'CONFIRMED' | 'CANCELLED'
export type PaymentMethod = 'CASH_ON_DELIVERY'
export type PaymentStatus = 'PENDING' | 'PAID'

export interface Order {
  id: number
  customerId: number
  customerName: string
  customerEmail: string
  status: OrderStatus
  paymentMethod: PaymentMethod | null
  paymentStatus: PaymentStatus | null
  items: OrderItem[]
  total: number
  createdAt: string | null
}

export interface OrderInput {
  customerId: number
  paymentMethod: PaymentMethod
  items: { productId: number; quantity: number }[]
}

export interface ApiErrorBody {
  timestamp?: string
  status?: number
  code?: string
  message?: string
  details?: Record<string, string>
}

export interface ProductInput {
  name: string
  description?: string | null
  price: number
  stockQuantity: number
  imageUrl?: string | null
  active?: boolean
  categoryId?: number | null
}

export interface CategoryInput {
  name: string
  description?: string | null
}