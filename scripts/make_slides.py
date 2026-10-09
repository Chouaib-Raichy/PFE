"""Genere soutenance/soutenance.pptx — a lancer: python scripts\\make_slides.py"""

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
GREEN = RGBColor(0x34, 0xD3, 0x99)

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
    if not path.exists():
        return
    slide.shapes.add_picture(str(path), x, y, width=w, height=int(w / SHOT_ASPECT))


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
_text(s, Inches(0.9), Inches(1.1), Inches(11.5), Inches(0.5),
      "UNIVERSITÉ HASSAN II — CASABLANCA · FSAC", 16, ACCENT, True)
_text(s, Inches(0.9), Inches(1.7), Inches(11.5), Inches(1.6),
      "Industrialisation d'une application web\nde gestion de projets", 40, TEXT, True)
_text(s, Inches(0.9), Inches(3.3), Inches(11.5), Inches(0.6),
      "CI/CD · Conteneurisation · Observabilité", 22, ACCENT)
bar = s.shapes.add_textbox(Inches(0.9), Inches(4.1), Inches(2.2), Inches(0.06))
bar.fill.solid()
bar.fill.fore_color.rgb = ACCENT
bar.line.fill.background()
_text(s, Inches(0.9), Inches(4.5), Inches(11.5), Inches(1.4),
      "Projet de fin d'études — Licence DevOps\n"
      "[Nom de l'étudiant]  ·  Encadrant : Pr. [Nom]\n"
      "Année universitaire 2025 / 2026", 17, MUTED)
_text(s, Inches(0.9), Inches(6.6), Inches(11.5), Inches(0.4),
      "Soutenance — [date]", 14, MUTED)

# ── 2. Plan ────────────────────────────────────────────────────────────────
s = _slide("Plan", "Soutenance — 15 à 20 minutes")
_plan = [
    ("1.", "Contexte & problématique", "DevOps, CI/CD, conteneurisation, observabilité"),
    ("2.", "Besoins & choix technologiques", "étude de l'existant, justifications"),
    ("3.", "Conception & architecture", "modèle de données, pipeline, observabilité"),
    ("4.", "Réalisation & démonstration", "Docker, GitHub Actions, Prometheus/Grafana"),
    ("5.", "Tests, résultats & perspectives", "99 % de couverture, démo live"),
]
y = 1.8
for num, title, sub in _plan:
    _text(s, Inches(0.9), Inches(y), Inches(0.8), Inches(0.5), num, 24, ACCENT, True)
    _text(s, Inches(1.7), Inches(y), Inches(6.5), Inches(0.5), title, 20, TEXT, True)
    _text(s, Inches(1.7), Inches(y + 0.42), Inches(10), Inches(0.4), sub, 14, MUTED)
    y += 1.0

# ── 3. Contexte ────────────────────────────────────────────────────────────
s = _slide("Contexte : pourquoi le DevOps ?", "De la cascade au flux continu")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.2), Inches(5), [
    "DevOps | rapprocher développement et exploitation, livrer plus vite et mieux.",
    "Intégration continue | chaque push déclenche tests + contrôles qualité.",
    "Déploiement continu | le code validé devient production sans manipulation.",
    "Conteneurisation | « ça marche sur ma machine » éliminé par l'image Docker.",
    "Observabilité | l'application expose son état et ses métriques en continu.",
], 16)
_mono(s, Inches(7.3), Inches(1.8), Inches(5.6), Inches(3),
      "commit\n  └─► lint\n        └─► tests\n              └─► build image\n"
      "                    └─► scan sécurité\n                          └─► publish\n"
      "                                └─► deploy", 15)
_text(s, Inches(7.3), Inches(5.3), Inches(5.6), Inches(1.5),
      "Indicateurs DORA : fréquence de déploiement, délai de mise en production,\n"
      "taux d'échec, temps de restauration.", 14, MUTED)

# ── 4. Problématique & objectifs ──────────────────────────────────────────
s = _slide("Problématique & objectifs")
_text(s, Inches(0.7), Inches(1.7), Inches(12), Inches(0.9),
      "Comment passer d'un code fonctionnel sur un poste de développement à une "
      "application livrable, reproductible, testée et observable ?", 19, ACCENT)
_bullets(s, Inches(0.7), Inches(2.9), Inches(6.0), Inches(4), [
    "Application web complète | CRUD projets, tâches, recherche, API REST.",
    "Image Docker multi-stage | non-root, healthcheck, couches cachées.",
    "Orchestration Compose | PostgreSQL, volumes, dépendances saines.",
    "Pipeline CI/CD | qualité → tests → build → scan → publication → déploiement.",
    "Observabilité as code | Prometheus + Grafana provisionnés en YAML/JSON.",
    "Qualité garantie | 9 tests, 99 % de couverture, seuil bloquant à 90 %.",
], 16)

# ── 5. Stack ───────────────────────────────────────────────────────────────
s = _slide("Choix technologiques", "Chaque outil justifié par un besoin")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Besoin", "Outil", "Justification"],
    ["Application", "Python 3.14 · FastAPI · Jinja2", "Rapide, OpenAPI auto, zéro build front"],
    ["Données", "SQLAlchemy 2 · PostgreSQL 17", "Typage moderne, standard industriel"],
    ["Conteneurs", "Docker (multi-stage) · Compose", "Reproductibilité, profils d'environnement"],
    ["CI/CD", "GitHub Actions · GHCR · Trivy", "Sans serveur, registre intégré, scan sécurité"],
    ["Qualité", "Ruff · pytest · pytest-cov", "Lint/format ultra-rapides, couverture ≥ 90 %"],
    ["Supervision", "Prometheus · Grafana", "Standard cloud-native, provisionné par code"],
], col_widths=[Inches(2.2), Inches(4.2), Inches(5.6)])

# ── 6. Architecture ────────────────────────────────────────────────────────
s = _slide("Architecture générale")
_mono(s, Inches(0.7), Inches(1.7), Inches(12), Inches(4.6),
      "                      ┌────────────────────────────────────────────┐\n"
      "                      │      GitHub Actions  (push / PR)          │\n"
      "                      │  quality ─► docker ─► deploy (optionnel)  │\n"
      "                      └──────────────────┬───────────────────────┘\n"
      "                                         │ build + push (GHCR)\n"
      "  Navigateur ────────► ┌──────────────────▼───────────────────────┐\n"
      "  :8000  app          │  app FastAPI (uvicorn, non-root, sain)   │\n"
      "  ◄── Prometheus ─────│  /metrics  /healthz  /api  /projects      │\n"
      "      scrape 5s       └──────────────────┬───────────────────────┘\n"
      "  Grafana :3000                          │ SQLAlchemy\n"
      "  dashboard auto     ┌───────────────────▼───────────────────────┐\n"
      "                     │  PostgreSQL :5432   volume db-data        │\n"
      "                     └────────────────────────────────────────────┘", 13)
_text(s, Inches(0.7), Inches(6.4), Inches(12), Inches(0.5),
      "Profil Compose « observability » : la pile de base reste app + base ; "
      "Prometheus/Grafana s'ajoutent en une commande.", 14, MUTED)

# ── 7. Application (screenshot) ────────────────────────────────────────────
s = _slide("L'application — tableau de bord", "Démo live : http://127.0.0.1:8000")
_shot(s, "app-dashboard.png", Inches(0.7), Inches(1.7), Inches(8.2))
_bullets(s, Inches(9.2), Inches(1.8), Inches(3.7), Inches(4.5), [
    "Compteurs | projets, tâches, avancement",
    "Recherche | titre ou étudiant",
    "Filtres | statut : en cours / soutenu / validé",
    "Barres d'avancement | par projet",
    "API Swagger | /api/docs",
], 15)

# ── 8. Application détail ──────────────────────────────────────────────────
s = _slide("L'application — suivi d'un projet", "Tâches, statut, date de soutenance")
_shot(s, "app-detail.png", Inches(0.7), Inches(1.7), Inches(8.2))
_bullets(s, Inches(9.2), Inches(1.8), Inches(3.7), Inches(4.5), [
    "Tâches | ajout + bascule terminé",
    "Statut | modification en un clic",
    "Encadrant | étudiant, date de soutenance",
    "API REST | JSON documenté OpenAPI",
    "Interface | 100 % hors-ligne (aucun CDN)",
], 15)

# ── 9. Conteneurisation ────────────────────────────────────────────────────
s = _slide("Conteneurisation", "Dockerfile multi-stage + Compose")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.2), Inches(5), [
    "Étage builder | venv isolé, pip sans cache, couches invalidées seulement si deps changent.",
    "Étage runtime | image finale légère : venv + code, sans outils de build.",
    "Sécurité | utilisateur non-root uid 10001, aucun secret dans l'image.",
    "Santé | HEALTHCHECK → Docker détecte et redémarre automatiquement.",
    "Compose | dépendance db : condition: service_healthy, volume persistant.",
    "Profils | --profile observability ajoute Prometheus + Grafana.",
], 16)
_mono(s, Inches(7.3), Inches(1.8), Inches(5.7), Inches(3.6),
      "FROM python:3.14-slim AS builder\n"
      "RUN python -m venv /opt/venv\n"
      "COPY requirements.txt .\nRUN pip install -r requirements.txt\n\n"
      "FROM python:3.14-slim\n"
      "RUN useradd --system --uid 10001 appuser\n"
      "COPY --from=builder /opt/venv /opt/venv\n"
      "COPY app ./app\nUSER appuser\n"
      "HEALTHCHECK CMD python -c \"urllib...healthz\"\n"
      'CMD ["uvicorn","app.main:app","--host","0.0.0.0"]', 13)

# ── 10. Pipeline CI/CD ─────────────────────────────────────────────────────
s = _slide("Pipeline CI/CD — GitHub Actions", "Trois jobs en cascade, chaque étape est un garde-fou")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Job", "Déclencheur", "Étapes"],
    ["quality", "push + pull request", "ruff check · ruff format · pytest --cov-fail-under=90 · artefact coverage"],
    ["docker", "push uniquement", "login GHCR · tags automatiques (sha, latest) · build+cache GHA · push · scan Trivy"],
    ["deploy", "main + DEPLOY_ENABLED", "SSH → docker compose pull && up -d (secrets : host, user, clé)"],
], col_widths=[Inches(1.7), Inches(3.0), Inches(7.3)], size=14)
_bullets(s, Inches(0.7), Inches(4.3), Inches(12), Inches(2.5), [
    "Quality gate | une couverture < 90 % casse la CI : pas d'image, pas de déploiement.",
    "Sécurité | aucun secret en dépôt — secrets GitHub injectés à l'exécution.",
    "Performance | cache de build GHA : reconstructions en quelques secondes.",
    "Traçabilité | chaque image taguée par le sha du commit déployé.",
], 16)

# ── 11. Observabilité ──────────────────────────────────────────────────────
s = _slide("Observabilité", "Métriques exposées → Prometheus → Grafana")
_bullets(s, Inches(0.7), Inches(1.7), Inches(6.3), Inches(4.6), [
    "Instrumentation | middleware : http_requests_total + histogramme de latence.",
    "Collecte | Prometheus scrape app:8000/metrics toutes les 5 s.",
    "Santé | /healthz vérifie app + base — sonde Docker et readiness.",
    "Provisioning | datasource + dashboard en YAML/JSON versionnés.",
    "Alerte potentielle | règles PromQL prêtes pour Alertmanager.",
], 16)
_mono(s, Inches(7.3), Inches(1.8), Inches(5.7), Inches(3.4),
      "sum by (status) (\n  rate(http_requests_total[1m]))\n\n"
      "histogram_quantile(0.95,\n  sum by (le) (\n"
      "    rate(http_request_duration_seconds_bucket[5m])))\n\n"
      'up{job="pfe-flow"}', 14)
_text(s, Inches(7.3), Inches(5.5), Inches(5.7), Inches(1),
      "Dashboard : débit par code HTTP, latence p95,\nprojets enregistrés, requêtes/min, disponibilité.",
      14, MUTED)

# ── 12. Grafana (screenshot) ───────────────────────────────────────────────
s = _slide("Dashboard Grafana", "Provisionné au démarrage — aucune configuration manuelle")
_shot(s, "grafana.png", Inches(2.47), Inches(1.7), Inches(8.4))

# ── 13. Tests & résultats ──────────────────────────────────────────────────
s = _slide("Tests & résultats")
for i, (big, small) in enumerate([("9", "tests fonctionnels"), ("99 %", "couverture de code"),
                                  ("90 %", "seuil bloquant CI"), ("4", "services conteneurisés")]):
    x = 0.7 + i * 3.15
    panel = s.shapes.add_textbox(Inches(x), Inches(1.8), Inches(2.9), Inches(1.5))
    panel.fill.solid()
    panel.fill.fore_color.rgb = PANEL
    panel.line.color.rgb = RGBColor(0x33, 0x41, 0x55)
    _text(s, Inches(x), Inches(2.0), Inches(2.9), Inches(0.8), big, 36, ACCENT, True,
          align=PP_ALIGN.CENTER)
    _text(s, Inches(x), Inches(2.8), Inches(2.9), Inches(0.4), small, 13, MUTED,
          align=PP_ALIGN.CENTER)
_mono(s, Inches(0.7), Inches(3.7), Inches(12), Inches(2.6),
      "$ pytest --cov=app\n9 passed in 1.97s\n\n"
      "Name              Stmts   Miss  Cover\n"
      "app/config.py         3      0   100%\n"
      "app/database.py      13      0   100%\n"
      "app/main.py         105      1    99%\n"
      "app/metrics.py        4      0   100%\n"
      "app/models.py        25      0   100%\n"
      "TOTAL               150      1    99%", 14)
_text(s, Inches(0.7), Inches(6.5), Inches(12), Inches(0.5),
      "Scénario de démo automatisé : scripts/demo.ps1 (stack, données, trafic, ouverture des interfaces).",
      14, MUTED)

# ── 14. Résultats de la pile ───────────────────────────────────────────────
s = _slide("Résultats de la pile complète", "Vérifié en conditions réelles")
_table(s, Inches(0.7), Inches(1.8), Inches(12), [
    ["Service", "URL", "Vérification"],
    ["Application", "127.0.0.1:8000", "/healthz → {status: ok, db: up} · données affichées"],
    ["API Swagger", "127.0.0.1:8000/api/docs", "Schéma OpenAPI généré automatiquement"],
    ["Prometheus", "127.0.0.1:9090", "Cible pfe-flow : state = up · échantillons collectés"],
    ["Grafana", "127.0.0.1:3000", "Dashboard « PFE Flow — Observabilité » provisionné"],
    ["CI/CD", "GitHub Actions", "quality → docker (GHCR, Trivy) → deploy (conditionnel)"],
], col_widths=[Inches(2.2), Inches(3.3), Inches(6.5)], size=14)
_bullets(s, Inches(0.7), Inches(5.0), Inches(12), Inches(2), [
    "Reproductibilité | une commande : docker compose --profile observability up -d --build",
    "Hors-ligne | aucune ressource CDN : la démo fonctionne sans Internet (hors push Git).",
], 16)

# ── 15. Perspectives ───────────────────────────────────────────────────────
s = _slide("Perspectives", "Ce qui prolonge naturellement le projet")
_bullets(s, Inches(0.7), Inches(1.9), Inches(12), Inches(4.5), [
    "Kubernetes | Deployments, Services, Ingress, autoscaling horizontal.",
    "GitOps | ArgoCD : la production suit l'état du dépôt Git.",
    "Logs & traces | Loki + OpenTelemetry : les trois piliers de l'observabilité.",
    "Alerting | règles Alertmanager → Slack/Email (latence, erreurs, disponibilité).",
    "Qualité SonarQube | dette technique et code smells.",
    "Tests de charge | k6 + SLO définis sur la latence p95.",
], 17)

# ── 16. Merci ──────────────────────────────────────────────────────────────
s = prs.slides.add_slide(BLANK)
_bg(s)
_text(s, Inches(0.9), Inches(2.6), Inches(11.5), Inches(1.2), "Merci de votre attention",
      44, TEXT, True, align=PP_ALIGN.CENTER)
_text(s, Inches(0.9), Inches(3.9), Inches(11.5), Inches(0.6),
      "Des questions ?", 24, ACCENT, align=PP_ALIGN.CENTER)
_text(s, Inches(0.9), Inches(5.0), Inches(11.5), Inches(1),
      "PFE Flow — Licence DevOps · FSAC Casablanca\n"
      "Démo : 127.0.0.1:8000 · Grafana : 127.0.0.1:3000", 15, MUTED, align=PP_ALIGN.CENTER)

OUT.parent.mkdir(exist_ok=True)
prs.save(OUT)
print(f"{OUT} — {len(prs.slides._sldIdLst)} slides")
