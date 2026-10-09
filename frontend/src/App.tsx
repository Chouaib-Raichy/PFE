import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import type { Project, ProjectInput, ProjectStatus, Stats } from './types'

const STATUSES: { value: ProjectStatus; label: string }[] = [
  { value: 'EN_COURS', label: 'En cours' },
  { value: 'SOUTENU', label: 'Soutenu' },
  { value: 'VALIDE', label: 'Validé' },
]

const EMPTY_FORM: ProjectInput = {
  title: '',
  student: '',
  supervisor: '',
  status: 'EN_COURS',
  description: '',
  defenseDate: '',
}

export default function App() {
  const [projects, setProjects] = useState<Project[]>([])
  const [stats, setStats] = useState<Stats>({ projects: 0, tasks: 0, done: 0 })
  const [q, setQ] = useState('')
  const [statusFilter, setStatusFilter] = useState<ProjectStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [form, setForm] = useState<ProjectInput>(EMPTY_FORM)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [showForm, setShowForm] = useState(false)

  const refresh = useCallback(async () => {
    setError('')
    try {
      const [list, s] = await Promise.all([api.listProjects(q, statusFilter), api.stats()])
      setProjects(list)
      setStats(s)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [q, statusFilter])

  useEffect(() => {
    const t = setTimeout(refresh, 200)
    return () => clearTimeout(t)
  }, [refresh])

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setEditingId(null)
    setShowForm(true)
  }

  const openEdit = (p: Project) => {
    setForm({
      title: p.title,
      student: p.student,
      supervisor: p.supervisor,
      status: p.status,
      description: p.description,
      defenseDate: p.defenseDate ?? '',
    })
    setEditingId(p.id)
    setShowForm(true)
  }

  const submitForm = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const payload: ProjectInput = { ...form, defenseDate: form.defenseDate || null }
      if (editingId === null) await api.createProject(payload)
      else await api.updateProject(editingId, payload)
      setShowForm(false)
      await refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const remove = async (p: Project) => {
    if (!confirm(`Supprimer le projet « ${p.title} » ?`)) return
    try {
      await api.deleteProject(p.id)
      await refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const addTask = async (projectId: number, label: string) => {
    if (!label.trim()) return
    try {
      await api.addTask(projectId, label)
      await refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const toggleTask = async (taskId: number) => {
    try {
      await api.toggleTask(taskId)
      await refresh()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const progress = stats.tasks === 0 ? 0 : Math.round((stats.done / stats.tasks) * 100)

  return (
    <div className="app">
      <header className="hero">
        <div>
          <h1>PFE Flow</h1>
          <p>Gestion des projets de fin d'études — FSAC</p>
        </div>
        <button className="primary" onClick={openCreate}>
          + Nouveau projet
        </button>
      </header>

      <section className="stats">
        <div className="stat">
          <span className="value">{stats.projects}</span>
          <span className="label">Projets</span>
        </div>
        <div className="stat">
          <span className="value">{stats.tasks}</span>
          <span className="label">Tâches</span>
        </div>
        <div className="stat">
          <span className="value">{stats.done}</span>
          <span className="label">Terminées</span>
        </div>
        <div className="stat">
          <span className="value">{progress}%</span>
          <span className="label">Avancement</span>
        </div>
      </section>

      <section className="toolbar">
        <input
          placeholder="Rechercher un projet, étudiant, encadrant…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as ProjectStatus | '')}
        >
          <option value="">Tous les statuts</option>
          {STATUSES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </section>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="muted">Chargement…</p>
      ) : projects.length === 0 ? (
        <p className="muted">Aucun projet. Créez-en un pour commencer.</p>
      ) : (
        <section className="grid">
          {projects.map((p) => (
            <ProjectCard
              key={p.id}
              project={p}
              onEdit={() => openEdit(p)}
              onDelete={() => remove(p)}
              onAddTask={(label) => addTask(p.id, label)}
              onToggleTask={toggleTask}
            />
          ))}
        </section>
      )}

      {showForm && (
        <div className="modal-backdrop" onClick={() => setShowForm(false)}>
          <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submitForm}>
            <h2>{editingId === null ? 'Nouveau projet' : 'Modifier le projet'}</h2>
            <label>
              Titre
              <input
                required
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
              />
            </label>
            <label>
              Étudiant
              <input
                required
                value={form.student}
                onChange={(e) => setForm({ ...form, student: e.target.value })}
              />
            </label>
            <label>
              Encadrant
              <input
                required
                value={form.supervisor}
                onChange={(e) => setForm({ ...form, supervisor: e.target.value })}
              />
            </label>
            <div className="row">
              <label>
                Statut
                <select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value as ProjectStatus })}
                >
                  {STATUSES.map((s) => (
                    <option key={s.value} value={s.value}>
                      {s.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Date de soutenance
                <input
                  type="date"
                  value={form.defenseDate ?? ''}
                  onChange={(e) => setForm({ ...form, defenseDate: e.target.value })}
                />
              </label>
            </div>
            <label>
              Description
              <textarea
                rows={3}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <div className="modal-actions">
              <button type="button" onClick={() => setShowForm(false)}>
                Annuler
              </button>
              <button type="submit" className="primary">
                Enregistrer
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}

function ProjectCard({
  project,
  onEdit,
  onDelete,
  onAddTask,
  onToggleTask,
}: {
  project: Project
  onEdit: () => void
  onDelete: () => void
  onAddTask: (label: string) => void
  onToggleTask: (taskId: number) => void
}) {
  const [newTask, setNewTask] = useState('')
  const total = project.tasks.length
  const pct = total === 0 ? 0 : Math.round((project.doneTasks / total) * 100)

  return (
    <article className="card">
      <div className="card-head">
        <span className={`badge ${project.status.toLowerCase()}`}>
          {STATUSES.find((s) => s.value === project.status)?.label}
        </span>
        <div className="card-actions">
          <button onClick={onEdit}>Modifier</button>
          <button className="danger" onClick={onDelete}>
            Supprimer
          </button>
        </div>
      </div>
      <h3>{project.title}</h3>
      <p className="meta">
        {project.student} · encadré par {project.supervisor}
      </p>
      {project.defenseDate && <p className="meta">Soutenance : {project.defenseDate}</p>}
      {project.description && <p className="desc">{project.description}</p>}

      <div className="progress">
        <div className="progress-bar" style={{ width: `${pct}%` }} />
      </div>
      <span className="muted small">
        {project.doneTasks}/{total} tâches
      </span>

      <ul className="tasks">
        {project.tasks.map((t) => (
          <li key={t.id}>
            <label>
              <input type="checkbox" checked={t.done} onChange={() => onToggleTask(t.id)} />
              <span className={t.done ? 'done' : ''}>{t.label}</span>
            </label>
          </li>
        ))}
      </ul>

      <form
        className="add-task"
        onSubmit={(e) => {
          e.preventDefault()
          onAddTask(newTask)
          setNewTask('')
        }}
      >
        <input
          placeholder="Ajouter une tâche…"
          value={newTask}
          onChange={(e) => setNewTask(e.target.value)}
        />
        <button type="submit">+</button>
      </form>
    </article>
  )
}