import { RouterProvider } from 'react-router-dom'
import { ToastProvider } from './ui/Toast'
import { AuthProvider } from './app/AuthProvider'
import { CartProvider } from './app/CartProvider'
import { router } from './app/router'

/**
 * Composition racine : identité (jeton Keycloak) -> panier (profil lié)
 * -> navigation. Rendu seulement après `initAuth()` (voir main.tsx).
 */
export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <CartProvider>
          <RouterProvider router={router} />
        </CartProvider>
      </AuthProvider>
    </ToastProvider>
  )
}