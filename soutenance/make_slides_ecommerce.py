"""Genere soutenance/Soutenance_PFE_Ecommerce_Microservices.pptx.

Diapositives de soutenance (16:9, fond noir, accents dore sobres).
Chiffres : rapports Surefire du 13/10/2026 (135 tests), docker ps (16 conteneurs),
Prometheus (7/7 cibles).

A lancer depuis la racine :  python soutenance\\make_slides_ecommerce.py
Les captures `ecom-*.png` doivent exister dans docs/assets/ ; sinon un cadre
« capture a inserer » est dessine (aucune ancienne image PFE Flow n'est utilisee).
"""

from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "soutenance" / "Soutenance_PFE_Ecommerce_Microservices.pptx"
ASSETS = ROOT / "docs" / "assets"

BG = RGBColor(0x0B, 0x0B, 0x0E)
PANEL = RGBColor(0x17, 0x17, 0x1B)
PANEL2 = RGBColor(0x1F, 0x1F, 0x24)
GOLD = RGBColor(0xC8, 0xA2, 0x4B)
TEXT = RGBColor(0xF5, 0xF5, 0xF6)
MUTED = RGBColor(0xA8, 0xA8, 0xB0)
LINE = RGBColor(0x33, 0x33, 0x38)

W, H = Inches(13.333), Inches(7.5)
SHOT_ASPECT = 1440 / 900

prs = Presentation()
prs.slide_width, prs.slide_height = W, H
BLANK = prs.slide_layouts[6]
_n = [0]


def _bg(slide):
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG


def _text(slide, x, y, w, h, text, size=18, color=TEXT, bold=False, font="Segoe UI",
          align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, italic=False):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    first = True
    for line in text.split("\n"):
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.alignment = align
        run = p.add_run()
        run.text = line
        run.font.size = Pt(size)
        run.font.color.rgb = color
        run.font.bold = bold
        run.font.italic = italic
        run.font.name = font
    return tf


def _bullets(slide, x, y, w, h, items, size=16, color=TEXT, space=10):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    for i, item in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(space)
        bold, sep, rest = item.partition("|")
        if sep:
            r1 = p.add_run()
            r1.text = "\u25aa " + bold.strip()
            r1.font.bold = True
            r1.font.size = Pt(size)
            r1.font.color.rgb = GOLD
            r1.font.name = "Segoe UI"
            r2 = p.add_run()
            r2.text = " " + rest.strip()
            r2.font.size = Pt(size)
            r2.font.color.rgb = color
            r2.font.name = "Segoe UI"
        else:
            r = p.add_run()
            r.text = "\u25aa " + item
            r.font.size = Pt(size)
            r.font.color.rgb = color
            r.font.name = "Segoe UI"
    return tf


def _bar(slide, x, y, w, h, color=GOLD):
    sh = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, x, y, w, h)
    sh.fill.solid()
    sh.fill.fore_color.rgb = color
    sh.line.fill.background()
    sh.shadow.inherit = False
    return sh


def _box(slide, x, y, w, h, text, fill=PANEL, line=LINE, size=13, bold=False,
         color=TEXT, align=PP_ALIGN.CENTER):
    sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, y, w, h)
    sh.fill.solid()
    sh.fill.fore_color.rgb = fill
    sh.line.color.rgb = line
    sh.line.width = Pt(1)
    sh.shadow.inherit = False
    tf = sh.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    tf.margin_left = Inches(0.12)
    tf.margin_right = Inches(0.12)
    first = True
    for ln in text.split("\n"):
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.alignment = align
        r = p.add_run()
        r.text = ln
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = color
        r.font.name = "Segoe UI"
    return sh


def _slide(title, kicker=None):
    _n[0] += 1
    s = prs.slides.add_slide(BLANK)
    _bg(s)
    _text(s, Inches(0.6), Inches(0.34), Inches(12.1), Inches(0.7), title, 28, TEXT, True)
    _bar(s, Inches(0.62), Inches(1.04), Inches(1.5), Inches(0.045))
    if kicker:
        _text(s, Inches(0.6), Inches(1.14), Inches(12.1), Inches(0.4), kicker, 13, MUTED)
    _text(s, Inches(0.6), Inches(7.05), Inches(9), Inches(0.3),
          "PFE — Plateforme e-commerce en microservices · FSAC", 10, MUTED)
    _text(s, Inches(12.4), Inches(7.05), Inches(0.7), Inches(0.3), str(_n[0]), 11, MUTED,
          align=PP_ALIGN.RIGHT)
    return s


def _mono(slide, x, y, w, h, code, size=13, color=TEXT):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = False
    first = True
    for ln in code.split("\n"):
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        r = p.add_run()
        r.text = ln
        r.font.size = Pt(size)
        r.font.name = "Consolas"
        r.font.color.rgb = color
    return tf


def _shot(slide, name, x, y, w):
    path = ASSETS / name
    height = int(w / SHOT_ASPECT)
    if path.exists():
        slide.shapes.add_picture(str(path), x, y, width=w, height=height)
        return
    ph = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, x, y, w, height)
    ph.fill.solid()
    ph.fill.fore_color.rgb = PANEL
    ph.line.color.rgb = LINE
    ph.shadow.inherit = False
    tf = ph.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    r = p.add_run()
    r.text = f"[ capture a inserer : docs/assets/{name} ]"
    r.font.size = Pt(14)
    r.font.color.rgb = MUTED
    r.font.name = "Segoe UI"


def _table(slide, x, y, w, rows, col_widths=None, size=13, header=True, rh=0.36):
    n_rows, n_cols = len(rows), len(rows[0])
    table = slide.shapes.add_table(n_rows, n_cols, x, y, w, Inches(rh * n_rows)).table
    if col_widths:
        for i, cw in enumerate(col_widths):
            table.columns[i].width = cw
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = table.cell(r, c)
            cell.text = val
            cell.fill.solid()
            cell.fill.fore_color.rgb = PANEL2 if (header and r == 0) else PANEL
            for p in cell.text_frame.paragraphs:
                for run in p.runs:
                    run.font.size = Pt(size)
                    run.font.name = "Segoe UI"
                    run.font.color.rgb = GOLD if (header and r == 0) else TEXT
                    run.font.bold = header and r == 0
            cell.margin_left = Inches(0.08)
            cell.margin_right = Inches(0.08)
            cell.margin_top = Inches(0.02)
            cell.margin_bottom = Inches(0.02)
    return table


# ── 1. Titre ────────────────────────────────────────────────────────────────
s = prs.slides.add_slide(BLANK)
_bg(s)
_bar(s, Inches(0.9), Inches(0.85), Inches(2.4), Inches(0.06))
_text(s, Inches(0.9), Inches(1.05), Inches(11.5), Inches(0.5),
      "UNIVERSITÉ HASSAN II DE CASABLANCA — FSAC", 15, GOLD, True)
_text(s, Inches(0.9), Inches(1.75), Inches(11.6), Inches(1.7),
      "Plateforme e-commerce\nen microservices", 40, TEXT, True)
_text(s, Inches(0.9), Inches(3.5), Inches(11.6), Inches(0.6),
      "Conteneurisation · Sécurité OIDC · Persistance polyglotte · Observabilité",
      18, MUTED)
_bar(s, Inches(0.9), Inches(4.3), Inches(2.4), Inches(0.06))
_text(s, Inches(0.9), Inches(4.7), Inches(11.6), Inches(1.6),
      "Projet de Fin d'Études — Filière DevOps\n"
      "Étudiant : Chouaib Raichy     ·     Encadrant : Pr. Noreddine Abghour\n"
      "Année universitaire 2025 / 2026", 16, MUTED)
_text(s, Inches(0.9), Inches(6.7), Inches(11.6), Inches(0.4),
      "Soutenance — [date à compléter]", 13, MUTED)

# ── 2. Plan ────────────────────────────────────────────────────────────────
s = _slide("Plan", "Soutenance — 15 à 20 minutes")
_plan = [
    ("01", "Contexte & problématique", "du monolithe aux microservices"),
    ("02", "Architecture & choix technologiques", "services, données, sécurité"),
    ("03", "Réalisation", "Docker Compose, Keycloak, observabilité"),
    ("04", "Tests & résultats", "135 tests, 16 conteneurs, 7/7 cibles"),
    ("05", "Perspectives", "industrialisation et prolongements"),
]
y = 1.85
for num, title, sub in _plan:
    _text(s, Inches(0.9), Inches(y - 0.05), Inches(0.9), Inches(0.5), num, 26, GOLD, True)
    _text(s, Inches(1.9), Inches(y), Inches(9.5), Inches(0.5), title, 20, TEXT, True)
    _text(s, Inches(1.9), Inches(y + 0.42), Inches(11), Inches(0.4), sub, 13, MUTED)
    y += 1.02

# ── 3. Contexte ────────────────────────────────────────────────────────────
s = _slide("Contexte : du monolithe aux microservices", "Modularité, résilience, scalabilité ciblée")
_bullets(s, Inches(0.7), Inches(1.75), Inches(6.2), Inches(5), [
    "Monolithe | un seul déployable : simple, mais couplage fort et scalabilité globale.",
    "Microservices | services autonomes par domaine, déployés indépendamment.",
    "Bénéfices | isolation des pannes, montée en charge ciblée, autonomie des équipes.",
    "Défis | découverte, configuration, sécurité distribuée, observabilité.",
    "Réponse | Spring Cloud (Config, Eureka, Gateway) + Keycloak + Prometheus/Grafana.",
], 15)
_mono(s, Inches(7.3), Inches(1.9), Inches(5.6), Inches(3.4),
      "Navigateur\n"
      "  └─► API Gateway :8090\n"
      "        ├─► product-service  :8081\n"
      "        ├─► customer-service :8082\n"
      "        ├─► cart-service     :8083\n"
      "        └─► order-service    :8084\n"
      "  Eureka = annuaire · Config = configuration", 14)
_text(s, Inches(7.3), Inches(5.2), Inches(5.6), Inches(1.6),
      "Un point d'entrée unique (la passerelle) porte le routage, le CORS et la "
      "sécurité ; les services restent privés sur le réseau Docker.", 14, MUTED)

# ── 4. Problématique & objectifs ──────────────────────────────────────────
s = _slide("Problématique & objectifs")
_text(s, Inches(0.7), Inches(1.75), Inches(12), Inches(0.9),
      "Comment passer de services développés séparément à une plateforme cohérente, "
      "reproductible, sécurisée et observable ?", 19, GOLD)
_bullets(s, Inches(0.7), Inches(2.95), Inches(12), Inches(4), [
    "Domaine | catalogue, clients, paniers, commandes.",
    "Architecture | 4 services métier + configuration + découverte + passerelle.",
    "Conteneurisation | Docker Compose : 16 conteneurs, volumes, healthchecks.",
    "Sécurité | OIDC (Keycloak), JWT, autorisation par rôle et par propriété.",
    "Persistance | polyglotte : PostgreSQL (référentiels) + MongoDB (agrégats).",
    "Recherche & stockage | Elasticsearch (recherche) + MinIO (images) + Redis (cache).",
    "Observabilité | 7 cibles Prometheus + dashboard Grafana.",
    "Qualité | 135 tests automatisés, tous au vert.",
], 15, space=8)

# ── 5. Stack ───────────────────────────────────────────────────────────────
s = _slide("Choix technologiques", "Chaque outil justifié par un besoin")
_table(s, Inches(0.7), Inches(1.85), Inches(12), [
    ["Besoin", "Outil", "Justification"],
    ["Services", "Java 21 · Spring Boot 3.3 · Spring Cloud", "Robustesse, écosystème microservices"],
    ["Données", "PostgreSQL 16 + MongoDB 7", "Polyglotte : relationnel + documentaire"],
    ["Recherche / cache", "Elasticsearch 8 · Redis 7", "Recherche full-text · cache de lecture"],
    ["Fichiers", "MinIO (S3)", "Stockage objet des images produit, compatible S3"],
    ["Identité", "Keycloak 26 (OIDC / OAuth2)", "Rôles et attributs, jetons JWT"],
    ["Frontend", "React 19 · Vite · TypeScript", "SPA typée, Authorization Code + PKCE"],
    ["Supervision", "Prometheus · Grafana", "Standard cloud-native, provisionné par code"],
    ["Orchestration", "Docker Compose · Kubernetes (manif.", "Pile locale reproductible · artefacts K8s"],
], col_widths=[Inches(2.1), Inches(4.7), Inches(5.2)], size=12)

# ── 6. Architecture (couches) ───────────────────────────────────────────────
s = _slide("Architecture générale", "Un point d'entrée, des services privés, une infra partagée")
_box(s, Inches(0.7), Inches(1.7), Inches(12), Inches(0.6),
     "Navigateur — SPA React 19 (Vite, TypeScript) · OAuth2 Authorization Code + PKCE", PANEL)
_box(s, Inches(0.7), Inches(2.45), Inches(12), Inches(0.6),
     "Keycloak 26 :8181 — fournisseur OIDC (realm pfeecom, jetons JWT)", PANEL2, GOLD, 13, True, GOLD)
_box(s, Inches(0.7), Inches(3.2), Inches(8.2), Inches(0.7),
     "API Gateway :8090 — routage · CORS · validation JWT", PANEL2, GOLD, 13, True, GOLD)
_box(s, Inches(9.1), Inches(3.2), Inches(3.6), Inches(0.7),
     "Eureka :8761 — annuaire de services", PANEL)
for i, (n, p) in enumerate([("product-service", "8081"), ("customer-service", "8082"),
                            ("cart-service", "8083"), ("order-service", "8084")]):
    _box(s, Inches(0.7 + i * 3.05), Inches(4.15), Inches(2.85), Inches(0.7),
         f"{n}\n:{p}", PANEL, LINE, 12, True)
_box(s, Inches(0.7), Inches(5.05), Inches(12), Inches(0.62),
     "PostgreSQL 16 :5432 (catalogue, clients) · MongoDB 7 :27017 (paniers, commandes) · "
     "Elasticsearch :9200 · MinIO :9000 · Redis :6379", PANEL, LINE, 12)
_box(s, Inches(0.7), Inches(5.82), Inches(12), Inches(0.62),
     "Config Server :8888 (configuration) · Prometheus :9090 (7 cibles) → Grafana :3000 (dashboard)",
     PANEL, LINE, 12)

# ── 7. Services & domaines ─────────────────────────────────────────────────
s = _slide("Découpage en services (bounded contexts)", "Un service par domaine — persistance polyglotte")
_table(s, Inches(0.7), Inches(1.85), Inches(12), [
    ["Service", "Domaine", "Base logique", "SGBD", "Port"],
    ["product-service", "Catalogue (produits, catégories)", "pfe_products", "PostgreSQL", "8081"],
    ["customer-service", "Clients (identité, contact)", "pfe_customers", "PostgreSQL", "8082"],
    ["cart-service", "Paniers (lignes, quantités)", "pfe_carts", "MongoDB", "8083"],
    ["order-service", "Commandes (validation, statut)", "pfe_orders", "MongoDB", "8084"],
    ["gateway / config / discovery", "Passerelle · configuration · annuaire", "—", "—", "8090/8888/8761"],
], col_widths=[Inches(3.1), Inches(3.6), Inches(1.9), Inches(1.7), Inches(1.7)], size=13, rh=0.42)

# ── 8. Persistance polyglotte ──────────────────────────────────────────────
s = _slide("Persistance polyglotte : SQL + NoSQL", "Un SGBD adapté à chaque type de données")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Famille", "Services", "SGBD", "Raison"],
    ["Référentiels", "catalogue, clients", "PostgreSQL 16 (JPA + Flyway)",
     "unicité, FK, CHECK, transactions ACID"],
    ["Agrégats", "paniers, commandes", "MongoDB 7",
     "lignes embarquées, snapshots, dénormalisation"],
], col_widths=[Inches(1.9), Inches(3.0), Inches(3.3), Inches(3.8)], size=13)
_bullets(s, Inches(0.7), Inches(3.5), Inches(12), Inches(3.4), [
    "Schéma & données | gérés par Flyway (V1 schéma, V2 seed, V3 provisioning), ddl-auto=validate.",
    "Intégrité | contraintes au niveau base : UNIQUE email/catégorie, FK produit→catégorie, CHECK prix/stock.",
    "Compléments | images produit sur MinIO, recherche sur Elasticsearch, cache de lecture sur Redis.",
    "Préservation | les données MongoDB historiques restent gelées (rollback possible).",
    "Limite | pas de transaction inter-bases — cohérence par snapshots et validation amont.",
], 14, space=8)

# ── 9. Sécurité OIDC ───────────────────────────────────────────────────────
s = _slide("Sécurité : OIDC / OAuth2 avec Keycloak", "Authentification fédérée, autorisation distribuée")
_bullets(s, Inches(0.7), Inches(1.75), Inches(6.1), Inches(4.9), [
    "Identité fédérée | Keycloak délivre les jetons (OIDC, Authorization Code + PKCE).",
    "JWT | signature, émetteur (iss) et audience vérifiés par chaque service.",
    "Resource servers | gateway, cart et order valident le jeton.",
    "Rôles | ADMIN gère le catalogue et les clients ; CUSTOMER achète.",
    "Propriété | le customer_id vient du jeton, jamais du navigateur.",
    "Résultat | un client ne voit que ses paniers et commandes (403 sinon).",
], 14, space=9)
_shot(s, "ecom-keycloak.png", Inches(7.0), Inches(1.85), Inches(5.6))
_text(s, Inches(7.0), Inches(5.5), Inches(5.7), Inches(1.3),
      "Le port de gestion (Actuator) de la passerelle est interne : aucune métrique "
      "n'est exposée sur le port public.", 13, MUTED)

# ── 10. Matrice d'autorisation ─────────────────────────────────────────────
s = _slide("Matrice d'autorisation", "Vérifiée de bout en bout : 401 · 403 · 200")
_table(s, Inches(0.7), Inches(1.95), Inches(12), [
    ["Ressource / opération", "Anonyme", "CUSTOMER", "ADMIN"],
    ["GET produits / catégories", "200", "200", "200"],
    ["Écriture produits / catégories", "401", "403", "OK"],
    ["GET liste des clients", "401", "403", "200"],
    ["Paniers / commandes (les siens)", "401", "OK", "OK"],
    ["Paniers / commandes (autre client)", "401", "403", "OK"],
], col_widths=[Inches(6.0), Inches(2.0), Inches(2.0), Inches(2.0)], size=14, rh=0.5)

# ── 11. Application (capture) ──────────────────────────────────────────────
s = _slide("L'application — boutique", "Démo : http://localhost:5173")
_shot(s, "ecom-app.png", Inches(0.7), Inches(1.75), Inches(8.3))
_bullets(s, Inches(9.3), Inches(1.85), Inches(3.6), Inches(4.6), [
    "Connexion | Keycloak (PKCE)",
    "Catalogue | produits paginés",
    "Recherche | Elasticsearch",
    "Panier | par client",
    "Commandes | suivi de statut",
    "API | Bearer token injecté",
], 14, space=9)

# ── 12. Conteneurisation ───────────────────────────────────────────────────
s = _slide("Conteneurisation & orchestration", "Docker Compose — 16 conteneurs")
_bullets(s, Inches(0.7), Inches(1.75), Inches(6.2), Inches(5), [
    "Images | une image par service (build Maven/JVM, frontend Nginx).",
    "Healthchecks | chaque service expose sa sonde de santé.",
    "Chaîne | depends_on condition: service_healthy (infra → services → gateway).",
    "Volumes | postgres/mongo/keycloak/prometheus/grafana : données persistantes.",
    "Réseau | services métier privés ; base sans port hôte ; points d'entrée publiés.",
    "Ports | gateway 8090, frontend 5173, keycloak 8181, prom 9090, grafana 3000.",
], 14, space=9)
_mono(s, Inches(7.3), Inches(1.9), Inches(5.7), Inches(4),
      "config-server ─┐\n"
      "               ├─► eureka ─► 4 services ─► gateway ─► frontend\n"
      "discovery ─────┘        ▲\n"
      "postgres·mongo·es·minio·redis ┘\n"
      "prometheus ─► scrape 7 ─► grafana\n\n"
      "docker compose -f docker-compose.ecom.yml up -d --build", 12)

# ── 13. Observabilité ──────────────────────────────────────────────────────
s = _slide("Observabilité", "Métriques exposées → Prometheus (7/7 cibles) → Grafana")
_bullets(s, Inches(0.7), Inches(1.75), Inches(6.1), Inches(4.9), [
    "Instrumentation | Spring Boot Actuator + Micrometer par service.",
    "Collecte | Prometheus scrape les 7 cibles (modèle pull).",
    "Export | /actuator/prometheus : compteurs + histogrammes de latence.",
    "Restitution | dashboard Grafana provisionné par code (11 panneaux).",
    "Isolation | Actuator de la passerelle sur un port de gestion interne.",
    "État vérifié | 7 cibles UP · 0 DOWN (capture ci-contre).",
], 14, space=9)
_shot(s, "ecom-prometheus.png", Inches(7.0), Inches(1.85), Inches(5.6))
_text(s, Inches(7.0), Inches(5.5), Inches(5.7), Inches(1.3),
      'Requêtes clés : up{job="pfe-ecom"} · histogram_quantile(0.95, …) · '
      "rate(http_server_requests_seconds_count[1m]).", 12, MUTED)

# ── 14. Grafana (capture) ──────────────────────────────────────────────────
s = _slide("Dashboard Grafana", "pfe-ecom-overview — provisionné au démarrage (11 panneaux)")
_shot(s, "ecom-grafana.png", Inches(2.42), Inches(1.72), Inches(8.5))

# ── 15. Tests & résultats ──────────────────────────────────────────────────
s = _slide("Tests & résultats")
for i, (big, small) in enumerate([("135", "tests au vert"), ("0", "échec / erreur"),
                                  ("16", "conteneurs"), ("7/7", "cibles Prometheus")]):
    x = 0.7 + i * 3.15
    _box(s, Inches(x), Inches(1.8), Inches(2.9), Inches(1.5), big, PANEL, LINE, 36, True, GOLD)
    _text(s, Inches(x), Inches(3.0), Inches(2.9), Inches(0.4), small, 13, MUTED,
          align=PP_ALIGN.CENTER)
_table(s, Inches(0.7), Inches(3.75), Inches(12), [
    ["Service", "Tests", "Répartition principale", "Résultat"],
    ["product-service", "52", "API 22 · images 8 · ES 6 · cache 7 · MinIO 8 · OpenAPI 1", "OK"],
    ["customer-service", "17", "API 9 · provisioning 7 · OpenAPI 1", "OK"],
    ["cart-service", "19", "API 12 · sécurité 6 · OpenAPI 1", "OK"],
    ["order-service", "17", "API 11 · sécurité 5 · OpenAPI 1", "OK"],
    ["api-gateway", "30", "sécurité 19 · propriété 6 · docs 5", "OK"],
], col_widths=[Inches(2.5), Inches(1.1), Inches(6.7), Inches(1.7)], size=12, rh=0.4)
_text(s, Inches(0.7), Inches(6.5), Inches(12), Inches(0.5),
      "Tests sur bases isolées (postgres-test :5433, mongo-test :27018) ; parcours API, "
      "sécurité (401/403/200) et contraintes de base. Total 135, dont 36 de sécurité "
      "rôle/propriété + 5 de sécurité des endpoints de documentation.", 12, MUTED)

# ── 16. Résultats de la pile ───────────────────────────────────────────────
s = _slide("Résultats de la pile complète", "Vérifié en conditions réelles")
_table(s, Inches(0.7), Inches(1.85), Inches(12), [
    ["Élément", "URL", "Vérification"],
    ["Boutique", "localhost:5173", "SPA servie, connexion Keycloak, catalogue affiché"],
    ["API produits", "localhost:8090/api/products", "200 public · CRUD réservé ADMIN"],
    ["Sécurité", "localhost:8181 (Keycloak)", "jetons JWT · matrice 401/403/200"],
    ["Prometheus", "localhost:9090", "7/7 cibles up · échantillons collectés"],
    ["Grafana", "localhost:3000", "dashboard pfe-ecom-overview (11 panneaux)"],
], col_widths=[Inches(2.4), Inches(4.2), Inches(5.4)], size=14)
_bullets(s, Inches(0.7), Inches(5.2), Inches(12), Inches(1.8), [
    "Reproductibilité | docker compose -f docker-compose.ecom.yml up -d --build (16 sains).",
    "Persistance | les volumes postgres + mongo survivent à un down / up (sans -v).",
    "Kubernetes | manifestes fournis et validés statiquement — non déployés dans ce livrable.",
], 14, space=8)

# ── 17. Perspectives ───────────────────────────────────────────────────────
s = _slide("Perspectives", "Ce qui prolonge naturellement le projet")
_bullets(s, Inches(0.7), Inches(1.9), Inches(12), Inches(4.5), [
    "Sécurité inter-services | jetons de service (client credentials) ou mTLS.",
    "Alerting | règles Prometheus + Alertmanager (latence, 5xx, cible DOWN).",
    "CI/CD | pipeline build + tests + publication d'images pour cette pile.",
    "Cohérence métier | décrément de stock, sagas pour les transactions distribuées.",
    "Traces & logs | OpenTelemetry + Loki : compléter les métriques.",
    "Industrialisation | déploiement Kubernetes (Ingress, HPA) et approche GitOps.",
], 16, space=10)

# ── 18. Merci ──────────────────────────────────────────────────────────────
s = prs.slides.add_slide(BLANK)
_bg(s)
_bar(s, Inches(5.47), Inches(2.3), Inches(2.4), Inches(0.06))
_text(s, Inches(0.9), Inches(2.7), Inches(11.5), Inches(1.2), "Merci de votre attention",
      42, TEXT, True, align=PP_ALIGN.CENTER)
_text(s, Inches(0.9), Inches(3.95), Inches(11.5), Inches(0.6),
      "Des questions ?", 22, GOLD, align=PP_ALIGN.CENTER)
_text(s, Inches(0.9), Inches(5.1), Inches(11.5), Inches(1),
      "Plateforme e-commerce microservices · Filière DevOps · FSAC Casablanca\n"
      "Démo : localhost:5173     ·     Grafana : localhost:3000", 14, MUTED,
      align=PP_ALIGN.CENTER)

OUT.parent.mkdir(exist_ok=True)
prs.save(OUT)
print(f"{OUT} — {len(prs.slides._sldIdLst)} slides")
