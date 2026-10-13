# docs/assets — captures d'écran

## Fichiers présents (LEGACY — ne pas utiliser)

Les images suivantes proviennent de l'**ancien projet « PFE Flow »** (monolithe
FastAPI/PostgreSQL). Elles **ne correspondent pas** à la stack e-commerce
microservices et ne doivent **pas** être utilisées dans le rapport, le guide ou
les slides :

- `app-dashboard.png`
- `app-detail.png`
- `grafana.png`
- `prometheus.png`

Elles sont conservées uniquement pour l'historique. Voir l'ancien projet dans
`archive/pfe-flow/`.

## Captures attendues (à prendre sur la pile e-commerce réelle)

Le générateur `soutenance/make_slides.py` insère automatiquement une capture si
le fichier correspondant existe ; sinon il dessine un cadre « capture à insérer ».
Déposer les captures ici avec ces noms exacts :

| Fichier attendu | Contenu | Source |
|---|---|---|
| `ecom-app.png` | SPA « ShopPFE » (catalogue / panier / commandes) | `http://localhost:5173` |
| `ecom-grafana.png` | Dashboard `pfe-ecom-overview` (11 panneaux avec données) | `http://localhost:3000` |
| `ecom-prometheus.png` | Page `/targets` : 7 cibles UP | `http://localhost:9090/targets` |
| `ecom-keycloak.png` | Écran de login du realm `pfeecom` (ou console d'admin) | `http://localhost:8181` |

Checklist détaillée : voir `GUIDE_SOUTENANCE.md`, section 11.
