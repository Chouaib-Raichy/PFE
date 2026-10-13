import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../app/AuthProvider'
import { useCart } from '../app/CartProvider'
import { initials } from '../lib/format'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'
import { SearchBox } from './SearchBox'

export function AppLayout() {
  const { authenticated, displayName, admin, login, register, logout, provisioning, provisionError, retryProvision } =
    useAuth()
  const { count } = useCart()
  const [drawer, setDrawer] = useState(false)

  const navLinks = (
    <>
      <NavLink to="/" end className="nav-link">
        Accueil
      </NavLink>
      <NavLink to="/catalog" className="nav-link">
        Catalogue
      </NavLink>
      {authenticated && (
        <NavLink to="/orders" className="nav-link">
          Commandes
        </NavLink>
      )}
      {admin && (
        <NavLink to="/admin_panel" className="nav-link">
          Administration
        </NavLink>
      )}
    </>
  )

  return (
    <div className="app-shell">
      <header className="header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark">S</span>
            <span>
              <div className="brand-name">ShopPFE</div>
              <div className="brand-sub">Boutique microservices — FSAC</div>
            </span>
          </Link>

          <nav className="nav" aria-label="Navigation principale">
            {navLinks}
          </nav>

          <SearchBox className="search-header" />

          <div className="header-actions">
            <Link to="/cart" className="icon-btn" aria-label={`Panier (${count})`}>
              <Icon name="cart" size={19} />
              {count > 0 && <span className="count-dot">{count > 99 ? '99+' : count}</span>}
            </Link>

            {authenticated ? (
              <>
                <Link to="/account" className="user-chip" aria-label="Mon compte">
                  <span className="avatar">{initials(displayName || '?')}</span>
                  <span
                    className="small nowrap"
                    style={{ maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis' }}
                  >
                    {displayName}
                  </span>
                </Link>

                <button
                  className="icon-btn"
                  onClick={logout}
                  aria-label="Déconnexion"
                  title="Déconnexion"
                >
                  <Icon name="logout" size={18} />
                </button>
              </>
            ) : (
              <div className="row gap-2">
                <Button variant="ghost" size="sm" onClick={() => login()}>
                  Connexion
                </Button>
                <Button variant="primary" size="sm" onClick={() => register()}>
                  Inscription
                </Button>
              </div>
            )}

            <button
              className="icon-btn menu-toggle"
              onClick={() => setDrawer(true)}
              aria-label="Ouvrir le menu"
            >
              <Icon name="menu" size={20} />
            </button>
          </div>
        </div>
      </header>

      {authenticated && (provisioning || provisionError) && (
        <div className="container" style={{ paddingTop: 8 }}>
          <div
            className={provisioning ? 'banner banner-info' : 'banner banner-error'}
            role="status"
            aria-live="polite"
          >
            {provisioning ? (
              <span>Finalisation de votre compte client…</span>
            ) : (
              <span className="row between" style={{ gap: 12, width: '100%' }}>
                <span>Votre profil client n'a pas pu être créé : {provisionError}</span>
                <Button variant="primary" size="sm" onClick={retryProvision}>
                  Réessayer
                </Button>
              </span>
            )}
          </div>
        </div>
      )}

      {drawer && (
        <>
          <div className="drawer-backdrop" onClick={() => setDrawer(false)} />
          <aside className="drawer" role="dialog" aria-label="Menu">
            <div className="between mb-4">
              <span className="semibold">Menu</span>
              <button className="icon-btn" onClick={() => setDrawer(false)} aria-label="Fermer">
                <Icon name="close" size={18} />
              </button>
            </div>
            <SearchBox className="mb-4" onNavigate={() => setDrawer(false)} />
            <nav aria-label="Navigation mobile" onClick={() => setDrawer(false)}>
              {navLinks}
            </nav>
          </aside>
        </>
      )}

      <main className="page">
        <div className="container">
          <Outlet />
        </div>
      </main>

      <footer className="footer">
        <div className="container footer-inner">
          <div>
            <div className="brand mb-4" style={{ cursor: 'default' }}>
              <span className="brand-mark">S</span>
              <span className="brand-name">ShopPFE</span>
            </div>
            <p className="muted small" style={{ maxWidth: '34ch' }}>
              Plateforme e-commerce en microservices — démonstration académique (FSAC Casablanca).
            </p>
          </div>
          <div>
            <h4>Boutique</h4>
            <ul>
              <li>
                <Link to="/catalog">Catalogue</Link>
              </li>
              <li>
                <Link to="/cart">Panier</Link>
              </li>
              <li>
                <Link to="/orders">Mes commandes</Link>
              </li>
            </ul>
          </div>
          <div>
            <h4>Compte</h4>
            <ul>
              {authenticated ? (
                <>
                  <li>
                    <Link to="/account">Profil</Link>
                  </li>
                  <li>
                    <button className="nav-link" style={{ padding: 0 }} onClick={logout}>
                      Déconnexion
                    </button>
                  </li>
                </>
              ) : (
                <>
                  <li>
                    <button className="nav-link" style={{ padding: 0 }} onClick={() => login()}>
                      Connexion
                    </button>
                  </li>
                  <li>
                    <button className="nav-link" style={{ padding: 0 }} onClick={() => register()}>
                      Inscription
                    </button>
                  </li>
                </>
              )}
            </ul>
          </div>
          <div>
            <h4>Plateforme</h4>
            <ul>
              <li>
                <a href="http://localhost:9090" target="_blank" rel="noreferrer">
                  Prometheus
                </a>
              </li>
              <li>
                <a href="http://localhost:3000" target="_blank" rel="noreferrer">
                  Grafana
                </a>
              </li>
            </ul>
          </div>
        </div>
        <div className="container footer-bottom">
          <span>© {new Date().getFullYear()} ShopPFE — PFE E-commerce</span>
          <span>React · Spring Boot · Keycloak · Docker</span>
        </div>
      </footer>
    </div>
  )
}
