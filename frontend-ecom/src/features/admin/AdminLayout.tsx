import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { initials } from '../../lib/format'
import { Icon } from '../../ui/Icon'

const NAV = [
  { to: '/admin_panel', end: true, label: "Vue d'ensemble", icon: 'grid' },
  { to: '/admin_panel/products', end: false, label: 'Produits', icon: 'box' },
  { to: '/admin_panel/categories', end: false, label: 'Catégories', icon: 'tag' },
  { to: '/admin_panel/customers', end: false, label: 'Clients', icon: 'users' },
  { to: '/admin_panel/orders', end: false, label: 'Commandes', icon: 'receipt' },
] as const

/** Coquille de l'espace d'administration : navigation latérale + barre supérieure. */
export function AdminLayout() {
  const { displayName, logout } = useAuth()

  return (
    <div className="admin-shell">
      <aside className="admin-side">
        <div className="admin-side-head">
          <span className="brand-mark">S</span>
          <span className="stack">
            <span className="semibold">Administration</span>
            <span className="muted xs">ShopPFE</span>
          </span>
        </div>

        <nav className="admin-nav" aria-label="Navigation administration">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="admin-nav-link">
              <Icon name={item.icon} size={18} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="admin-side-foot">
          <Link to="/" className="admin-nav-link">
            <Icon name="arrowRight" size={18} />
            <span>Retour à la boutique</span>
          </Link>
        </div>
      </aside>

      <div className="admin-main">
        <div className="admin-topbar">
          <span className="admin-topbar-title">Console d'administration</span>
          <div className="row gap-3">
            <span className="user-chip" aria-label="Administrateur connecté">
              <span className="avatar">{initials(displayName || '?')}</span>
              <span className="small nowrap"> {displayName}</span>
            </span>
            <button className="icon-btn" onClick={logout} aria-label="Déconnexion" title="Déconnexion">
              <Icon name="logout" size={18} />
            </button>
          </div>
        </div>

        <div className="admin-content">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
