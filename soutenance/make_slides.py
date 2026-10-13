"""Genere soutenance/soutenance.pptx (plateforme e-commerce microservices).

A lancer depuis la racine du projet :
    .venv\\Scripts\\python.exe soutenance\\make_slides.py

Les captures d'ecran attendues se nomment `ecom-*.png` dans `docs/assets/`.
Tant qu'une capture est absente, un cadre « capture a inserer » est dessine :
aucune ancienne image (PFE Flow) n'est utilisee.
"""

from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "soutenance" / "soutenance.pptx"
ASSETS = ROOT / "docs" / "assets"

BG = RGBColor(0x0F, 0x17, 0x2A)
PANEL = RGBColor(0x1E, 0x29, 0x3B)
ACCENT = RGBColor(0x38, 0xBD, 0xF8)
TEXT = RGBColor(0xE2, 0xE8, 0xF0)
MUTED = RGBColor(0x94, 0xA3, 0xB8)

W, H = Inches(13.333), Inches(7.5)
SHOT_ASPECT = 1400 / 900

prs = Presentation()
prs.slide_width, prs.slide_height = W, H
BLANK = prs.slide_layouts[6]
_n = [0]


def _bg(slide):
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG


def _text(slide, x, y, w, h, text, size=18, color=TEXT, bold=False, font="Segoe UI",
          align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text
    run.font.size = Pt(size)
    run.font.color.rgb = color
    run.font.bold = bold
    run.font.name = font
    return tf


def _bullets(slide, x, y, w, h, items, size=17, color=TEXT):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    for i, item in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(10)
        bold, sep, rest = item.partition("|")
        if sep:
            run = p.add_run()
            run.text = "• " + bold.strip()
            run.font.bold = True
            run.font.size = Pt(size)
            run.font.color.rgb = ACCENT
            run.font.name = "Segoe UI"
            run2 = p.add_run()
            run2.text = " " + rest.strip()
            run2.font.size = Pt(size)
            run2.font.color.rgb = color
            run2.font.name = "Segoe UI"
        else:
            run = p.add_run()
            run.text = "• " + item
            run.font.size = Pt(size)
            run.font.color.rgb = color
            run.font.name = "Segoe UI"
    return tf


def _slide(title, kicker=None):
    _n[0] += 1
    s = prs.slides.add_slide(BLANK)
    _bg(s)
    _text(s, Inches(0.6), Inches(0.32), Inches(12.1), Inches(0.8), title, 30, TEXT, True)
    if kicker:
        _text(s, Inches(0.6), Inches(1.0), Inches(12.1), Inches(0.4), kicker, 14, MUTED)
    accent = s.shapes.add_textbox(Inches(0.6), Inches(1.32), Inches(1.6), Inches(0.05))
    accent.fill.solid()
    accent.fill.fore_color.rgb = ACCENT
    accent.line.fill.background()
    _text(s, Inches(12.4), Inches(7.05), Inches(0.7), Inches(0.3), str(_n[0]), 11, MUTED,
          align=PP_ALIGN.RIGHT)
    return s


def _mono(slide, x, y, w, h, code, size=13):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = False
    p = tf.paragraphs[0]
    run = p.add_run()
    run.text = code
    run.font.size = Pt(size)
    run.font.name = "Consolas"
    run.font.color.rgb = TEXT
    return tf


def _shot(slide, name, x, y, w):
    path = ASSETS / name
    height = int(w / SHOT_ASPECT)
    if path.exists():
        slide.shapes.add_picture(str(path), x, y, width=w, height=height)
        return
    ph = slide.shapes.add_textbox(x, y, w, height)
    ph.fill.solid()
    ph.fill.fore_color.rgb = PANEL
    ph.line.color.rgb = RGBColor(0x33, 0x41, 0x55)
    tf = ph.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    run = p.add_run()
    run.text = f"[ capture a inserer : docs/assets/{name} ]"
    run.font.size = Pt(14)
    run.font.color.rgb = MUTED
    run.font.name = "Segoe UI"


def _table(slide, x, y, w, rows, col_widths=None, size=14, header=True):
    n_rows, n_cols = len(rows), len(rows[0])
    table = slide.shapes.add_table(n_rows, n_cols, x, y, w, Inches(0.4 * n_rows)).table
    if col_widths:
        for i, cw in enumerate(col_widths):
            table.columns[i].width = cw
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = table.cell(r, c)
            cell.text = val
            cell.fill.solid()
            cell.fill.fore_color.rgb = PANEL if not (header and r == 0) else RGBColor(0x24, 0x34, 0x4D)
            for p in cell.text_frame.paragraphs:
                for run in p.runs:
                    run.font.size = Pt(size)
                    run.font.name = "Segoe UI"
                    run.font.color.rgb = ACCENT if (header and r == 0) else TEXT
                    run.font.bold = header and r == 0
            cell.margin_left = Inches(0.08)
            cell.margin_right = Inches(0.08)
            cell.margin_top = Inches(0.03)
            cell.margin_bottom = Inches(0.03)
    return table


# ── 1. Titre ────────────────────────────────────────────────────────────────
s = prs.slides.add_slide(BLANK)
_bg(s)
_text(s, Inches(0.9), Inches(1.0), Inches(11.5), Inches(0.5),
      "UNIVERSITÉ HASSAN II — CASABLANCA · FSAC", 16, ACCENT, True)
_text(s, Inches(0.9), Inches(1.6), Inches(11.5), Inches(1.6),
      "Plateforme e-commerce\nen microservices", 40, TEXT, True)
_text(s, Inches(0.9), Inches(3.25), Inches(11.5), Inches(0.6),
      "Persistance polyglotte · Sécurité OIDC · Observabilité", 22, ACCENT)
bar = s.shapes.add_textbox(Inches(0.9), Inches(4.05), Inches(2.2), Inches(0.06))
bar.fill.solid()
bar.fill.fore_color.rgb = ACCENT
bar.line.fill.background()
_text(s, Inches(0.9), Inches(4.45), Inches(11.5), Inches(1.4),
      "Projet de fin d'études — Licence DevOps\n"
      "[Nom de l'étudiant]  ·  Encadrant : Pr. [Nom]\n"
      "Année universitaire 2025 / 2026", 17, MUTED)
_text(s, Inches(0.9), Inches(6.6), Inches(11.5), Inches(0.4),
      "Soutenance — [date]", 14, MUTED)

# ── 2. Plan ────────────────────────────────────────────────────────────────
s = _slide("Plan", "Soutenance — 15 à 20 minutes")
_plan = [
    ("1.", "Contexte & problématique", "du monolithe aux microservices"),
    ("2.", "Besoins & choix technologiques", "étude de l'existant, justifications"),
    ("3.", "Conception & architecture", "services, sécurité, observabilité"),
    ("4.", "Réalisation & démonstration", "Docker, Keycloak, Prometheus/Grafana"),
    ("5.", "Tests, résultats & perspectives", "52 tests, 7 cibles, 13 conteneurs"),
]
y = 1.8
for num, title, sub in _plan:
    _text(s, Inches(0.9), Inches(y), Inches(0.8), Inches(0.5), num, 24, ACCENT, True)
    _text(s, Inches(1.7), Inches(y), Inches(6.5), Inches(0.5), title, 20, TEXT, True)
    _text(s, Inches(1.7), Inches(y + 0.42), Inches(10), Inches(0.4), sub, 14, MUTED)
    y += 1.0

# ── 3. Contexte ────────────────────────────────────────────────────────────
s = _slide("Contexte : du monolithe aux microservices", "Modularité, résilience, scalabilité ciblée")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.2), Inches(5), [
    "Monolithe | un seul déployable : simple, mais couplage fort et scalabilité globale.",
    "Microservices | services autonomes par domaine, déployés indépendamment.",
    "Bénéfices | isolation des pannes, équipes et montées en charge ciblées.",
    "Défis | découverte, configuration, sécurité distribuée, observabilité.",
    "Réponse | briques Spring Cloud + Keycloak + Prometheus/Grafana.",
], 16)
_mono(s, Inches(7.3), Inches(1.8), Inches(5.6), Inches(3),
      "Navigateur\n  └─► API Gateway\n        ├─► product-service\n"
      "        ├─► customer-service\n        ├─► cart-service\n"
      "        └─► order-service\n"
      "  (Eureka = annuaire · Config Server = config)", 14)
_text(s, Inches(7.3), Inches(5.0), Inches(5.6), Inches(1.8),
      "Un point d'entrée unique (la passerelle) porte le routage, le CORS et la "
      "sécurité ; les services restent privés sur le réseau Docker.", 14, MUTED)

# ── 4. Problématique & objectifs ──────────────────────────────────────────
s = _slide("Problématique & objectifs")
_text(s, Inches(0.7), Inches(1.7), Inches(12), Inches(0.9),
      "Comment passer de services développés séparément à une plateforme cohérente, "
      "reproductible, sécurisée et observable ?", 19, ACCENT)
_bullets(s, Inches(0.7), Inches(2.9), Inches(12), Inches(4), [
    "Domaine | catalogue, clients, paniers, commandes.",
    "Architecture | 4 services métier + config + découverte + passerelle.",
    "Conteneurisation | Docker Compose : 13 conteneurs, volumes, healthchecks.",
    "Sécurité | OIDC (Keycloak), JWT, autorisation par rôle et par propriété.",
    "Persistance | polyglotte : PostgreSQL (référentiels) + MongoDB (agrégats).",
    "Observabilité | métriques Prometheus (7 cibles) + dashboard Grafana.",
    "Qualité | 52 tests automatisés, tous au vert.",
], 16)

# ── 5. Stack ───────────────────────────────────────────────────────────────
s = _slide("Choix technologiques", "Chaque outil justifié par un besoin")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Besoin", "Outil", "Justification"],
    ["Services", "Java 21 · Spring Boot 3.3 · Spring Cloud", "Robustesse, écosystème microservices"],
    ["Données", "PostgreSQL 16 + MongoDB 7", "Polyglotte : relationnel + documentaire"],
    ["Identité", "Keycloak 26 (OIDC / OAuth2)", "Rôles et attributs, jetons JWT"],
    ["Frontend", "React 19 · Vite · TypeScript", "SPA typée, Authorization Code + PKCE"],
    ["Supervision", "Prometheus · Grafana", "Standard cloud-native, provisionné par code"],
    ["Orchestration", "Docker Compose", "Pile locale reproductible (13 conteneurs)"],
], col_widths=[Inches(2.2), Inches(4.6), Inches(5.2)])

# ── 6. Architecture ────────────────────────────────────────────────────────
s = _slide("Architecture générale")
_mono(s, Inches(0.5), Inches(1.7), Inches(12.3), Inches(4.8),
      "                         ┌───────────────────────────┐\n"
      "                         │      Keycloak :8181        │  fournisseur OIDC\n"
      "                         └─────────────▲─────────────┘\n"
      "  Navigateur (SPA React)               │ jetons JWT\n"
      "        │ Authorization Code + PKCE    │\n"
      "        ▼                              │\n"
      "  ┌────────────────────────┐    ┌──────┴───────┐\n"
      "  │  API Gateway :8090     │◄──►│ Eureka :8761 │  annuaire de services\n"
      "  │  routage · CORS · JWT  │    └──────────────┘\n"
      "  └───┬───────┬───────┬────┘\n"
      "      ▼       ▼       ▼\n"
      "  product  customer  cart  order     services REST (privés au réseau Docker)\n"
      "    :8081   :8082   :8083  :8084\n"
      "      └───────┴───┬───┴───────┘\n"
      "                  ▼\n"
      "        PostgreSQL :5432 · MongoDB :27017 · Config Server :8888\n"
      "  Prometheus :9090 scrape 7 cibles ──► Grafana :3000 (dashboard)", 12)

# ── 7. Services & domaines ─────────────────────────────────────────────────
s = _slide("Découpage en services (bounded contexts)", "Un service par domaine — persistance polyglotte")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Service", "Domaine", "Base logique", "SGBD", "Port interne"],
    ["product-service", "Catalogue (produits, catégories)", "pfe_products", "PostgreSQL", "8081"],
    ["customer-service", "Clients (identité, contact)", "pfe_customers", "PostgreSQL", "8082"],
    ["cart-service", "Paniers (lignes, quantités)", "pfe_carts", "MongoDB", "8083"],
    ["order-service", "Commandes (validation, statut)", "pfe_orders", "MongoDB", "8084"],
    ["api-gateway / config / discovery", "Passerelle · configuration · annuaire", "—", "—", "8090 / 8888 / 8761"],
], col_widths=[Inches(3.2), Inches(3.4), Inches(1.9), Inches(1.7), Inches(1.8)], size=13)

# ── 7bis. Persistance polyglotte ───────────────────────────────────────────
s = _slide("Persistance polyglotte : SQL + NoSQL", "Un SGBD adapté à chaque type de données")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Famille", "Services", "SGBD", "Raison"],
    ["Référentiels", "catalogue, clients", "PostgreSQL 16 (JPA + Flyway)",
     "unicité, FK, CHECK, transactions ACID"],
    ["Agrégats", "paniers, commandes", "MongoDB 7 (inchangé)",
     "lignes embarquées, snapshots, dénormalisation"],
], col_widths=[Inches(1.9), Inches(3.1), Inches(3.4), Inches(3.6)], size=13)
_bullets(s, Inches(0.7), Inches(3.5), Inches(12), Inches(3.2), [
    "Schéma & données | gérés par Flyway (V1 schéma, V2 seed), ddl-auto=validate.",
    "Intégrité | contraintes au niveau base : UNIQUE email/catégorie, FK produit→catégorie, CHECK prix/stock.",
    "Périmètre | migration minimale (2 services / 4) ; contrats d'API et sécurité inchangés.",
    "Préservation | les données MongoDB historiques restent gelées (rollback possible).",
    "Limite | pas de transaction inter-bases — cohérence par snapshots et validation amont.",
], 14)

# ── 8. Sécurité OIDC ───────────────────────────────────────────────────────
s = _slide("Sécurité : OIDC / OAuth2 avec Keycloak", "Authentification fédérée, autorisation distribuée")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.3), Inches(4.8), [
    "Identité fédérée | Keycloak délivre les jetons (OIDC, Authorization Code + PKCE).",
    "JWT | signature, émetteur (iss) et audience vérifiés par chaque service.",
    "Resource servers | gateway, cart et order valident le jeton.",
    "Rôles | ADMIN gère le catalogue et les clients ; CUSTOMER achète.",
    "Propriété | le customer_id vient du jeton, jamais du navigateur.",
    "Résultat | un client ne voit que ses paniers et commandes (403 sinon).",
], 15)
_mono(s, Inches(7.3), Inches(1.9), Inches(5.6), Inches(2.6),
      "requireOwnership(auth, customerId):\n"
      "  if isAdmin(auth): return       # bypass\n"
      "  own = customerId(auth)         # claim JWT\n"
      "  if own != customerId:\n"
      "     -> AccessDenied (403)", 13)
_text(s, Inches(7.3), Inches(4.7), Inches(5.6), Inches(2),
      "Le port de gestion (Actuator) de la passerelle est interne : aucune métrique "
      "n'est exposée sur le port public.", 14, MUTED)

# ── 9. Matrice d'autorisation ──────────────────────────────────────────────
s = _slide("Matrice d'autorisation", "Vérifiée de bout en bout : 401 · 403 · 200")
_table(s, Inches(0.7), Inches(1.9), Inches(12), [
    ["Ressource / opération", "Anonyme", "CUSTOMER", "ADMIN"],
    ["GET produits / catégories", "200", "200", "200"],
    ["Écriture produits / catégories", "401", "403", "OK"],
    ["GET liste des clients", "401", "403", "200"],
    ["Paniers / commandes (les siens)", "401", "OK", "OK"],
    ["Paniers / commandes (autre client)", "401", "403", "OK"],
], col_widths=[Inches(6.0), Inches(2.0), Inches(2.0), Inches(2.0)])

# ── 10. Application (capture) ──────────────────────────────────────────────
s = _slide("L'application — boutique", "Démo : http://localhost:5173")
_shot(s, "ecom-app.png", Inches(0.7), Inches(1.7), Inches(8.2))
_bullets(s, Inches(9.2), Inches(1.8), Inches(3.7), Inches(4.5), [
    "Connexion | via Keycloak (PKCE)",
    "Catalogue | produits paginés",
    "Panier | par client",
    "Commandes | suivi du statut",
    "API | Bearer token injecté",
], 15)

# ── 11. Conteneurisation ───────────────────────────────────────────────────
s = _slide("Conteneurisation & orchestration", "Docker Compose — 13 conteneurs")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.2), Inches(5), [
    "Images | une image par service (build Maven/JVM, frontend Nginx).",
    "Healthchecks | chaque service expose sa sonde de santé.",
    "Chaîne | depends_on condition: service_healthy (infra → services → gateway).",
    "Volumes | postgres/mongo/keycloak/prometheus/grafana : données persistantes.",
    "Réseau | services métier privés ; PostgreSQL sans port hôte ; points d'entrée publiés.",
    "Ports | gateway 8090, frontend 5173, keycloak 8181, prom 9090, grafana 3000.",
], 15)
_mono(s, Inches(7.3), Inches(1.9), Inches(5.7), Inches(4),
      "config-server ─┐\n"
      "               ├─► eureka ─► 4 services ─► gateway ─► frontend\n"
      "discovery ─────┘        ▲\n"
      "postgres · mongo · keycloak ┘\n"
      "prometheus ─► scrape 7 ─► grafana\n\n"
      "docker compose -f docker-compose.ecom.yml up -d --build", 12)

# ── 12. Observabilité ──────────────────────────────────────────────────────
s = _slide("Observabilité", "Métriques exposées → Prometheus → Grafana")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.3), Inches(4.8), [
    "Instrumentation | Spring Boot Actuator + Micrometer par service.",
    "Collecte | Prometheus scrape les 7 cibles (modèle pull).",
    "Export | /actuator/prometheus : compteurs + histogrammes de latence.",
    "Restitution | dashboard Grafana provisionné par code (11 panneaux).",
    "Isolation | Actuator du gateway sur un port de gestion interne.",
], 15)
_mono(s, Inches(7.3), Inches(1.9), Inches(5.7), Inches(3.2),
      "sum by (application) (\n"
      "  rate(http_server_requests_seconds_count[1m]))\n\n"
      "histogram_quantile(0.95,\n"
      "  sum by (le) (\n"
      "    rate(http_server_requests_seconds_bucket[5m])))\n\n"
      'up{job="pfe-ecom"}', 12)

# ── 13. Grafana (capture) ──────────────────────────────────────────────────
s = _slide("Dashboard Grafana", "pfe-ecom-overview — provisionné au démarrage")
_shot(s, "ecom-grafana.png", Inches(1.3), Inches(1.7), Inches(10.6))

# ── 14. Tests & résultats ──────────────────────────────────────────────────
s = _slide("Tests & résultats")
for i, (big, small) in enumerate([("52", "tests au vert"), ("0", "échec / erreur"),
                                  ("13", "conteneurs"), ("7/7", "cibles Prometheus")]):
    x = 0.7 + i * 3.15
    panel = s.shapes.add_textbox(Inches(x), Inches(1.8), Inches(2.9), Inches(1.5))
    panel.fill.solid()
    panel.fill.fore_color.rgb = PANEL
    panel.line.color.rgb = RGBColor(0x33, 0x41, 0x55)
    _text(s, Inches(x), Inches(2.0), Inches(2.9), Inches(0.8), big, 36, ACCENT, True,
          align=PP_ALIGN.CENTER)
    _text(s, Inches(x), Inches(2.8), Inches(2.9), Inches(0.4), small, 13, MUTED,
          align=PP_ALIGN.CENTER)
_table(s, Inches(0.7), Inches(3.6), Inches(12), [
    ["Service", "Tests", "Résultat"],
    ["product-service / customer-service", "12 / 9", "OK"],
    ["cart-service / order-service", "13 / 12", "OK"],
    ["api-gateway", "6", "OK"],
], col_widths=[Inches(6.5), Inches(2.5), Inches(3.0)], size=14)
_text(s, Inches(0.7), Inches(6.4), Inches(12), Inches(0.5),
      "Tests sur bases isolées (postgres-test + mongo-test) ; parcours API, sécurité (401/403/200) et contraintes de base.",
      14, MUTED)

# ── 15. Résultats de la pile ───────────────────────────────────────────────
s = _slide("Résultats de la pile complète", "Vérifié en conditions réelles")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Élément", "URL", "Vérification"],
    ["Boutique", "localhost:5173", "SPA servie, connexion Keycloak, données affichées"],
    ["API produits", "localhost:8090/api/products", "200 public · CRUD réservé ADMIN"],
    ["Sécurité", "localhost:8181 (Keycloak)", "jetons JWT · matrice 401/403/200"],
    ["Prometheus", "localhost:9090", "7/7 cibles up · échantillons collectés"],
    ["Grafana", "localhost:3000", "dashboard pfe-ecom-overview (11 panneaux)"],
], col_widths=[Inches(2.4), Inches(4.2), Inches(5.4)], size=14)
_bullets(s, Inches(0.7), Inches(5.1), Inches(12), Inches(2), [
    "Reproductibilité | docker compose -f docker-compose.ecom.yml up -d --build (13 sains)",
    "Persistance | les volumes postgres + mongo survivent à un down / up (sans -v).",
], 15)

# ── 16. Perspectives ───────────────────────────────────────────────────────
s = _slide("Perspectives", "Ce qui prolonge naturellement le projet")
_bullets(s, Inches(0.7), Inches(1.9), Inches(12), Inches(4.5), [
    "Sécurité inter-services | jetons de service (client credentials) ou mTLS.",
    "Alerting | règles Prometheus + Alertmanager (latence, 5xx, cible DOWN).",
    "CI/CD | pipeline build + tests + publication d'images pour cette pile.",
    "Cohérence métier | décrément de stock, sagas pour les transactions distribuées.",
    "Traces & logs | OpenTelemetry + Loki : compléter les métriques.",
    "Industrialisation | Kubernetes (Ingress, HPA) et approche GitOps.",
], 16)

# ── 17. Merci ──────────────────────────────────────────────────────────────
s = prs.slides.add_slide(BLANK)
_bg(s)
_text(s, Inches(0.9), Inches(2.6), Inches(11.5), Inches(1.2), "Merci de votre attention",
      44, TEXT, True, align=PP_ALIGN.CENTER)
_text(s, Inches(0.9), Inches(3.9), Inches(11.5), Inches(0.6),
      "Des questions ?", 24, ACCENT, align=PP_ALIGN.CENTER)
_text(s, Inches(0.9), Inches(5.0), Inches(11.5), Inches(1),
      "Plateforme e-commerce microservices · Licence DevOps · FSAC Casablanca\n"
      "Démo : localhost:5173 · Grafana : localhost:3000", 15, MUTED, align=PP_ALIGN.CENTER)

OUT.parent.mkdir(exist_ok=True)
prs.save(OUT)
print(f"{OUT} — {len(prs.slides._sldIdLst)} slides")
