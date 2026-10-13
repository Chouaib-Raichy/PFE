import { useMemo, useState } from 'react'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { formatDate } from '../../lib/format'
import { useToast } from '../../ui/Toast'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Button } from '../../ui/Button'
import { Icon } from '../../ui/Icon'
import { Modal } from '../../ui/Modal'
import { ConfirmDialog } from '../../ui/ConfirmDialog'
import { EmptyState } from '../../ui/EmptyState'
import { Skeleton } from '../../ui/Skeleton'
import { Field, Input, Textarea } from '../../ui/Field'
import type { Category } from '../../types'

interface FormState {
  name: string
  description: string
}

const EMPTY_FORM: FormState = { name: '', description: '' }

export function CategoriesAdminPage() {
  const { notify } = useToast()
  const { data, loading, error, reload } = useAsync(() => api.listCategories(), [])

  const [query, setQuery] = useState('')
  const [editor, setEditor] = useState<{ open: boolean; category: Category | null }>({ open: false, category: null })
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [toDelete, setToDelete] = useState<Category | null>(null)
  const [deleting, setDeleting] = useState(false)

  const filtered = useMemo(() => {
    const list = data ?? []
    const q = query.trim().toLowerCase()
    if (!q) return list
    return list.filter(
      (c) => c.name.toLowerCase().includes(q) || (c.description ?? '').toLowerCase().includes(q),
    )
  }, [data, query])

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setErrors({})
    setEditor({ open: true, category: null })
  }

  const openEdit = (category: Category) => {
    setForm({ name: category.name, description: category.description ?? '' })
    setErrors({})
    setEditor({ open: true, category })
  }

  const save = async () => {
    const errs: Record<string, string> = {}
    const name = form.name.trim()
    if (!name) errs.name = 'Le nom est obligatoire.'
    else if (name.length > 100) errs.name = '100 caractères maximum.'
    if (form.description.length > 500) errs.description = '500 caractères maximum.'
    setErrors(errs)
    if (Object.keys(errs).length > 0) return

    setSaving(true)
    try {
      const payload = { name, description: form.description.trim() || null }
      if (editor.category) {
        await api.updateCategory(editor.category.id, payload)
        notify('Catégorie mise à jour.')
      } else {
        await api.createCategory(payload)
        notify('Catégorie créée.')
      }
      setEditor({ open: false, category: null })
      reload()
    } catch (err) {
      notify((err as Error).message, 'error')
    } finally {
      setSaving(false)
    }
  }

  const confirmDelete = async () => {
    if (!toDelete) return
    setDeleting(true)
    try {
      await api.deleteCategory(toDelete.id)
      notify('Catégorie supprimée.')
      setToDelete(null)
      reload()
    } catch (err) {
      notify((err as Error).message, 'error')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Administration', to: '/admin_panel' }, { label: 'Catégories' }]} />
        <h1 className="mt-4">Catégories</h1>
        <p className="muted mt-2">{loading ? 'Chargement…' : `${filtered.length} catégorie(s)`}</p>
      </div>

      <div className="admin-toolbar">
        <div className="search-input admin-search">
          <span className="icon">
            <Icon name="search" size={16} />
          </span>
          <input
            className="input"
            placeholder="Rechercher une catégorie…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Rechercher une catégorie"
          />
        </div>
        <Button variant="primary" onClick={openCreate}>
          <Icon name="plus" size={16} /> Nouvelle catégorie
        </Button>
      </div>

      {loading && (
        <div className="card card-pad stack gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} height={42} />
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState tone="error" message={error} actionLabel="Réessayer" onAction={reload} />
      )}

      {!loading && !error && filtered.length === 0 && (
        <EmptyState
          icon="tag"
          title={query ? 'Aucun résultat' : 'Aucune catégorie'}
          message={
            query ? 'Aucune catégorie ne correspond à cette recherche.' : 'Créez votre première catégorie.'
          }
          actionLabel={query ? undefined : 'Nouvelle catégorie'}
          onAction={query ? undefined : openCreate}
        />
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Nom</th>
                <th>Description</th>
                <th>Créée le</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr key={c.id}>
                  <td className="semibold">{c.name}</td>
                  <td style={{ whiteSpace: 'normal' }}>{c.description ?? <span className="muted">—</span>}</td>
                  <td className="muted">{formatDate(c.createdAt)}</td>
                  <td>
                    <div className="actions">
                      <button
                        className="icon-btn"
                        title="Modifier"
                        aria-label={`Modifier ${c.name}`}
                        onClick={() => openEdit(c)}
                      >
                        <Icon name="edit" size={16} />
                      </button>
                      <button
                        className="icon-btn"
                        title="Supprimer"
                        aria-label={`Supprimer ${c.name}`}
                        onClick={() => setToDelete(c)}
                      >
                        <Icon name="trash" size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={editor.open}
        title={editor.category ? 'Modifier la catégorie' : 'Nouvelle catégorie'}
        onClose={() => setEditor({ open: false, category: null })}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditor({ open: false, category: null })} disabled={saving}>
              Annuler
            </Button>
            <Button variant="primary" onClick={() => void save()} loading={saving}>
              Enregistrer
            </Button>
          </>
        }
      >
        <div className="stack gap-4">
          <Field label="Nom" required error={errors.name}>
            {(id) => (
              <Input
                id={id}
                value={form.name}
                aria-invalid={!!errors.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            )}
          </Field>
          <Field label="Description" error={errors.description}>
            {(id) => (
              <Textarea
                id={id}
                value={form.description}
                aria-invalid={!!errors.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            )}
          </Field>
        </div>
      </Modal>

      <ConfirmDialog
        open={toDelete !== null}
        title="Supprimer la catégorie"
        danger
        loading={deleting}
        confirmLabel="Supprimer"
        message={
          toDelete ? (
            <>
              Supprimer « <strong>{toDelete.name}</strong> » ? Une catégorie encore utilisée par des produits
              ne peut pas être supprimée.
            </>
          ) : (
            ''
          )
        }
        onConfirm={() => void confirmDelete()}
        onClose={() => setToDelete(null)}
      />
    </section>
  )
}
