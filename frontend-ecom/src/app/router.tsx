import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { RequireAdmin } from './RequireAdmin'
import { RequireAuth } from './RequireAuth'
import { HomePage } from '../features/catalog/HomePage'
import { CatalogPage } from '../features/catalog/CatalogPage'
import { SearchPage } from '../features/catalog/SearchPage'
import { ProductDetailPage } from '../features/catalog/ProductDetailPage'
import { CartPage } from '../features/cart/CartPage'
import { OrdersPage } from '../features/orders/OrdersPage'
import { OrderDetailPage } from '../features/orders/OrderDetailPage'
import { ProfilePage } from '../features/account/ProfilePage'
import { AdminLayout } from '../features/admin/AdminLayout'
import { AdminHomePage } from '../features/admin/AdminHomePage'
import { ProductsAdminPage } from '../features/admin/ProductsAdminPage'
import { CategoriesAdminPage } from '../features/admin/CategoriesAdminPage'
import { CustomersAdminPage } from '../features/admin/CustomersAdminPage'
import { OrdersAdminPage } from '../features/admin/OrdersAdminPage'
import { ForbiddenPage } from '../pages/ForbiddenPage'
import { NotFoundPage } from '../pages/NotFoundPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'catalog', element: <CatalogPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'products/:id', element: <ProductDetailPage /> },
      { path: 'cart', element: <CartPage /> },
      { path: 'checkout', element: <CartPage /> },
      {
        path: 'orders',
        element: (
          <RequireAuth>
            <OrdersPage />
          </RequireAuth>
        ),
      },
      {
        path: 'orders/:id',
        element: (
          <RequireAuth>
            <OrderDetailPage />
          </RequireAuth>
        ),
      },
      {
        path: 'account',
        element: (
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        ),
      },
      {
        path: 'account/profile',
        element: (
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        ),
      },
      {
        path: 'account/orders',
        element: (
          <RequireAuth>
            <OrdersPage />
          </RequireAuth>
        ),
      },
      {
        path: 'admin_panel',
        element: (
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        ),
        children: [
          { index: true, element: <AdminHomePage /> },
          { path: 'products', element: <ProductsAdminPage /> },
          { path: 'categories', element: <CategoriesAdminPage /> },
          { path: 'customers', element: <CustomersAdminPage /> },
          { path: 'orders', element: <OrdersAdminPage /> },
          { path: 'orders/:id', element: <OrderDetailPage admin /> },
        ],
      },
      { path: 'admin', element: <Navigate to="/admin_panel" replace /> },
      { path: 'admin/*', element: <Navigate to="/admin_panel" replace /> },
      { path: '403', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
