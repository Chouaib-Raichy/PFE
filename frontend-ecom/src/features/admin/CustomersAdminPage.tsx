import { useMemo, useState } from 'react'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { formatDate, initials, isValidEmail } from '../../lib/format'
import { useToast } from '../../ui/Toast'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Button } from '../../ui/Button'
import { Icon } from '../../ui/Icon'
import { Modal } from '../../ui/Modal'
import { ConfirmDialog } from '../../ui/ConfirmDialog'
import { EmptyState } from '../../ui/EmptyState'
import { Skeleton } from '../../ui/Skeleton'
import { Field, Input } from '../../ui/Field'
import type { Customer, CustomerInput } from '../../types'

interface FormState {
  firstName: string
  lastName: string
  email: string
  phone: string
}

const EMPTY_FORM: FormState = { firstName: '', lastName: '', email: '', phone: '' }

export function CustomersAdminPage() {
  const { notify } = useToast()
  const { data, loading, error, reload } = useAsync(() => api.listCustomers(0, 100), [])

  const [query, setQuery] = useState('')
  const [editor, setEditor] = useState<{ open: boolean; customer: Customer | null }>({ open: false, customer: null })
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [toDelete, setToDelete] = useState<Customer | null>(null)
  const [deleting, setDeleting] = useState(false)

  const customers = useMemo(() => data?.content ?? [], [data])
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return customers
    return customers.filter((c) =>
      `${c.firstName} ${c.lastName} ${c.email}`.toLowerCase().includes(q),
    )
  }, [customers, query])

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setErrors({})
    setEditor({ open: true, customer: null })
  }

  const openEdit = (customer: Customer) => {
    setForm({
      firstName: customer.firstName,
      lastName: customer.lastName,
      email: customer.email,
      phone: customer.phone ?? '',
    })
    setErrors({})
    setEditor({ open: true, customer })
  }

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const save = async () => {
    const errs: Record<string, string> = {}
    if (!form.firstName.trim()) errs.firstName = 'Le prénom est obligatoire.'
    if (!form.lastName.trim()) errs.lastName = 'Le nom est obligatoire.'
    if (!form.email.trim()) errs.email = "L'e-mail est obligatoire."
    else if (!isValidEmail(form.email)) errs.email = 'Adresse e-mail invalide.'
    setErrors(errs)
    if (Object.keys(errs).length > 0) return

    const payload: CustomerInput = {
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim() || undefined,
    }

    setSaving(true)
    try {
      if (editor.customer) {
        await api.updateCustomer(editor.customer.id, payload)
        notify('Client mis à jour.')
      } else {
        await api.createCustomer(payload)
        notify('Client créé.')
      }
      setEditor({ open: false, customer: null })
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
      await api.deleteCustomer(toDelete.id)
      notify('Client supprimé.')
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
        <Breadcrumbs items={[{ label: 'Administration', to: '/admin_panel' }, { label: 'Clients' }]} />
        <h1 className="mt-4">Clients</h1>
        <p className="muted mt-2">{loading ? 'Chargement…' : `${filtered.length} client(s)`}</p>
      </div>

      <div className="admin-toolbar">
        <div className="search-input admin-search">
          <span className="icon">
            <Icon name="search" size={16} />
          </span>
          <input
            className="input"
            placeholder="Rechercher un client…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Rechercher un client"
          />
        </div>
        <Button variant="primary" onClick={openCreate}>
          <Icon name="plus" size={16} /> Nouveau client
        </Button>
      </div>

      {loading && (
        <div className="card card-pad stack gap-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} height={42} />
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState tone="error" message={error} actionLabel="Réessayer" onAction={reload} />
      )}

      {!loading && !error && filtered.length === 0 && (
        <EmptyState
          icon="users"
          title={query ? 'Aucun résultat' : 'Aucun client'}
          message={query ? 'Aucun client ne correspond à cette recherche.' : 'Créez votre premier client.'}
          actionLabel={query ? undefined : 'Nouveau client'}
          onAction={query ? undefined : openCreate}
        />
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Client</th>
                <th>E-mail</th>
                <th>Téléphone</th>
                <th>Inscrit le</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr key={c.id}>
                  <td>
                    <span className="row gap-3">
                      <span className="avatar">{initials(`${c.firstName} ${c.lastName}`)}</span>
                      <span className="stack">
                        <span className="semibold">
                          {c.firstName} {c.lastName}
                        </span>
                        <span className="muted xs">#{c.id}</span>
                      </span>
                    </span>
                  </td>
                  <td>{c.email}</td>
                  <td>{c.phone ?? <span className="muted">—</span>}</td>
                  <td className="muted">{formatDate(c.createdAt)}</td>
                  <td>
                    <div className="actions">
                      <button
                        className="icon-btn"
                        title="Modifier"
                        aria-label={`Modifier ${c.firstName} ${c.lastName}`}
                        onClick={() => openEdit(c)}
                      >
                        <Icon name="edit" size={16} />
                      </button>
                      <button
                        className="icon-btn"
                        title="Supprimer"
                        aria-label={`Supprimer ${c.firstName} ${c.lastName}`}
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
        title={editor.customer ? 'Modifier le client' : 'Nouveau client'}
        onClose={() => setEditor({ open: false, customer: null })}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditor({ open: false, customer: null })} disabled={saving}>
              Annuler
            </Button>
            <Button variant="primary" onClick={() => void save()} loading={saving}>
              Enregistrer
            </Button>
          </>
        }
      >
        <div className="form-grid">
          <Field label="Prénom" required error={errors.firstName}>
            {(id) => (
              <Input
                id={id}
                value={form.firstName}
                aria-invalid={!!errors.firstName}
                onChange={(e) => set('firstName', e.target.value)}
              />
            )}
          </Field>
          <Field label="Nom" required error={errors.lastName}>
            {(id) => (
              <Input
                id={id}
                value={form.lastName}
                aria-invalid={!!errors.lastName}
                onChange={(e) => set('lastName', e.target.value)}
              />
            )}
          </Field>
          <div className="span-2">
            <Field label="E-mail" required error={errors.email}>
              {(id) => (
                <Input
                  id={id}
                  type="email"
                  value={form.email}
                  aria-invalid={!!errors.email}
                  onChange={(e) => set('email', e.target.value)}
                />
              )}
            </Field>
          </div>
          <div className="span-2">
            <Field label="Téléphone" hint="Optionnel">
              {(id) => <Input id={id} value={form.phone} onChange={(e) => set('phone', e.target.value)} />}
            </Field>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={toDelete !== null}
        title="Supprimer le client"
        danger
        loading={deleting}
        confirmLabel="Supprimer"
        message={
          toDelete ? (
            <>
              Supprimer <strong>{toDelete.firstName} {toDelete.lastName}</strong> ? Cette action est
              irréversible.
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
