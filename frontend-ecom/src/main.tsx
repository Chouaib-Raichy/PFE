import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { initAuth } from './lib/auth'
import './styles/index.css'

const rootElement = document.getElementById('root')!
const root = createRoot(rootElement)

root.render(
  <StrictMode>
    <div className="splash">
      <div className="brand">
        <span className="brand-mark">S</span>
        <span className="brand-name">ShopPFE</span>
      </div>
      <p className="muted small">Chargement de la boutique…</p>
    </div>
  </StrictMode>,
)

initAuth()
  .then(() => {
    root.render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })
  .catch((err: unknown) => {
    root.render(
      <StrictMode>
        <div className="splash">
          <p className="error" role="alert">
            Authentification impossible : {String(err)}
          </p>
          <button className="btn btn-primary" onClick={() => window.location.reload()}>
            Réessayer
          </button>
        </div>
      </StrictMode>,
    )
  })