import { useEffect, useMemo, useState } from 'react'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { formatPrice } from '../../lib/format'
import { useToast } from '../../ui/Toast'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Button } from '../../ui/Button'
import { Badge } from '../../ui/Badge'
import { Icon } from '../../ui/Icon'
import { Modal } from '../../ui/Modal'
import { ConfirmDialog } from '../../ui/ConfirmDialog'
import { EmptyState } from '../../ui/EmptyState'
import { Skeleton } from '../../ui/Skeleton'
import { Field, Input, Select, Textarea } from '../../ui/Field'
import type { Product, ProductInput } from '../../types'

// ponytail: chargement unique (size=100) + filtre client ; basculer sur la
// pagination serveur si le catalogue dépasse cette taille.
const LOAD_SIZE = 100

interface FormState {
  name: string
  description: string
  price: string
  stockQuantity: string
  categoryId: string
  active: boolean
}

const EMPTY_FORM: FormState = {
  name: '',
  description: '',
  price: '',
  stockQuantity: '0',
  categoryId: '',
  active: true,
}

export function ProductsAdminPage() {
  const { notify } = useToast()
  const { data, loading, error, reload } = useAsync(async () => {
    const [products, categories] = await Promise.all([api.listProducts(0, LOAD_SIZE), api.listCategories()])
    return { products: products.content, categories }
  }, [])

  const [query, setQuery] = useState('')
  const [editor, setEditor] = useState<{ open: boolean; product: Product | null }>({ open: false, product: null })
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [toDelete, setToDelete] = useState<Product | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [removeImage, setRemoveImage] = useState(false)
  const [uploadPct, setUploadPct] = useState(0)

  useEffect(() => {
    if (!imageFile) {
      setImagePreview(null)
      return
    }
    const url = URL.createObjectURL(imageFile)
    setImagePreview(url)
    return () => URL.revokeObjectURL(url)
  }, [imageFile])

  const resetImage = () => {
    setImageFile(null)
    setRemoveImage(false)
    setUploadPct(0)
  }

  const categories = data?.categories ?? []
  const filtered = useMemo(() => {
    const list = data?.products ?? []
    const q = query.trim().toLowerCase()
    if (!q) return list
    return list.filter(
      (p) => p.name.toLowerCase().includes(q) || (p.categoryName ?? '').toLowerCase().includes(q),
    )
  }, [data, query])

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setErrors({})
    resetImage()
    setEditor({ open: true, product: null })
  }

  const openEdit = (product: Product) => {
    setForm({
      name: product.name,
      description: product.description ?? '',
      price: String(product.price),
      stockQuantity: String(product.stockQuantity),
      categoryId: product.categoryId != null ? String(product.categoryId) : '',
      active: product.active,
    })
    setErrors({})
    resetImage()
    setEditor({ open: true, product })
  }

  const onPickImage = (file: File | null) => {
    setRemoveImage(false)
    setImageFile(file)
  }

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const validate = (): Record<string, string> => {
    const errs: Record<string, string> = {}
    const name = form.name.trim()
    if (!name) errs.name = 'Le nom est obligatoire.'
    else if (name.length > 150) errs.name = '150 caractères maximum.'
    if (form.description.length > 1000) errs.description = '1000 caractères maximum.'

    const price = Number(form.price)
    if (form.price.trim() === '' || Number.isNaN(price) || price <= 0) {
      errs.price = 'Le prix doit être strictement positif.'
    }
    const stock = Number(form.stockQuantity)
    if (form.stockQuantity.trim() === '' || !Number.isInteger(stock) || stock < 0) {
      errs.stockQuantity = 'Le stock doit être un entier positif ou nul.'
    }
    return errs
  }

  const save = async () => {
    const errs = validate()
    setErrors(errs)
    if (Object.keys(errs).length > 0) return

    const payload: ProductInput = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      price: Number(form.price),
      stockQuantity: Number(form.stockQuantity),
      active: form.active,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
    }

    setSaving(true)
    try {
      const saved = editor.product
        ? await api.updateProduct(editor.product.id, payload)
        : await api.createProduct(payload)

      if (imageFile) {
        setUploadPct(0)
        await api.uploadProductImage(saved.id, imageFile, setUploadPct)
        notify(editor.product ? 'Produit et image mis à jour.' : 'Produit créé avec image.')
      } else {
        if (removeImage && editor.product?.imageUrl) {
          await api.deleteProductImage(saved.id)
        }
        notify(editor.product ? 'Produit mis à jour.' : 'Produit créé.')
      }

      setEditor({ open: false, product: null })
      resetImage()
      reload()
    } catch (err) {
      notify((err as Error).message, 'error')
    } finally {
      setSaving(false)
      setUploadPct(0)
    }
  }

  const toggleActive = async (product: Product) => {
    try {
      await api.updateProduct(product.id, {
        name: product.name,
        description: product.description,
        price: product.price,
        stockQuantity: product.stockQuantity,
        active: !product.active,
        categoryId: product.categoryId,
      })
      notify(product.active ? 'Produit désactivé.' : 'Produit activé.')
      reload()
    } catch (err) {
      notify((err as Error).message, 'error')
    }
  }

  const confirmDelete = async () => {
    if (!toDelete) return
    setDeleting(true)
    try {
      await api.deleteProduct(toDelete.id)
      notify('Produit supprimé.')
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
        <Breadcrumbs items={[{ label: 'Administration', to: '/admin_panel' }, { label: 'Produits' }]} />
        <h1 className="mt-4">Produits</h1>
        <p className="muted mt-2">
          {loading ? 'Chargement…' : `${filtered.length} produit(s) sur ${data?.products.length ?? 0}`}
        </p>
      </div>

      <div className="admin-toolbar">
        <div className="search-input admin-search">
          <span className="icon">
            <Icon name="search" size={16} />
          </span>
          <input
            className="input"
            placeholder="Rechercher un produit…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Rechercher un produit"
          />
        </div>
        <Button variant="primary" onClick={openCreate}>
          <Icon name="plus" size={16} /> Nouveau produit
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
          icon="box"
          title={query ? 'Aucun résultat' : 'Aucun produit'}
          message={query ? 'Aucun produit ne correspond à cette recherche.' : 'Créez votre premier produit.'}
          actionLabel={query ? undefined : 'Nouveau produit'}
          onAction={query ? undefined : openCreate}
        />
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Produit</th>
                <th>Catégorie</th>
                <th>Prix</th>
                <th>Stock</th>
                <th>Statut</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <tr key={p.id}>
                  <td style={{ whiteSpace: 'normal' }}>
                    <span className="semibold">{p.name}</span>
                    {p.description && <div className="muted xs">{p.description}</div>}
                  </td>
                  <td>{p.categoryName ?? <span className="muted">—</span>}</td>
                  <td className="semibold">{formatPrice(p.price)}</td>
                  <td>
                    {p.stockQuantity === 0 ? (
                      <Badge tone="danger">Épuisé</Badge>
                    ) : (
                      <span>{p.stockQuantity}</span>
                    )}
                  </td>
                  <td>
                    <Badge tone={p.active ? 'success' : 'neutral'}>{p.active ? 'Actif' : 'Inactif'}</Badge>
                  </td>
                  <td>
                    <div className="actions">
                      <button
                        className="icon-btn"
                        title="Modifier"
                        aria-label={`Modifier ${p.name}`}
                        onClick={() => openEdit(p)}
                      >
                        <Icon name="edit" size={16} />
                      </button>
                      <button
                        className="icon-btn"
                        title={p.active ? 'Désactiver' : 'Activer'}
                        aria-label={p.active ? `Désactiver ${p.name}` : `Activer ${p.name}`}
                        onClick={() => void toggleActive(p)}
                      >
                        <Icon name={p.active ? 'close' : 'check'} size={16} />
                      </button>
                      <button
                        className="icon-btn"
                        title="Supprimer"
                        aria-label={`Supprimer ${p.name}`}
                        onClick={() => setToDelete(p)}
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
        title={editor.product ? 'Modifier le produit' : 'Nouveau produit'}
        onClose={() => setEditor({ open: false, product: null })}
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditor({ open: false, product: null })} disabled={saving}>
              Annuler
            </Button>
            <Button variant="primary" onClick={() => void save()} loading={saving}>
              Enregistrer
            </Button>
          </>
        }
      >
        <div className="form-grid">
          <div className="span-2">
            <Field label="Nom" required error={errors.name}>
              {(id) => (
                <Input
                  id={id}
                  value={form.name}
                  aria-invalid={!!errors.name}
                  onChange={(e) => set('name', e.target.value)}
                />
              )}
            </Field>
          </div>
          <div className="span-2">
            <Field label="Description" error={errors.description}>
              {(id) => (
                <Textarea
                  id={id}
                  value={form.description}
                  aria-invalid={!!errors.description}
                  onChange={(e) => set('description', e.target.value)}
                />
              )}
            </Field>
          </div>
          <Field label="Prix" required error={errors.price}>
            {(id) => (
              <Input
                id={id}
                type="number"
                min="0"
                step="0.01"
                value={form.price}
                aria-invalid={!!errors.price}
                onChange={(e) => set('price', e.target.value)}
              />
            )}
          </Field>
          <Field label="Stock" required error={errors.stockQuantity}>
            {(id) => (
              <Input
                id={id}
                type="number"
                min="0"
                step="1"
                value={form.stockQuantity}
                aria-invalid={!!errors.stockQuantity}
                onChange={(e) => set('stockQuantity', e.target.value)}
              />
            )}
          </Field>
          <Field label="Catégorie">
            {(id) => (
              <Select id={id} value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)}>
                <option value="">— Aucune —</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <div className="field">
            <span className="field-label">Statut</span>
            <label className="check-row">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => set('active', e.target.checked)}
              />
              <span>Produit actif (visible au catalogue)</span>
            </label>
          </div>
          <div className="span-2">
            <div className="field">
              <span className="field-label">Image</span>
              <div className="image-field">
                <span className="image-field-preview">
                  {(() => {
                    const current = imagePreview ?? (removeImage ? null : editor.product?.imageUrl ?? null)
                    return current ? (
                      <img src={current} alt="" />
                    ) : (
                      <Icon name="image" size={40} />
                    )
                  })()}
                </span>
                <div className="stack gap-2">
                  <label className="btn btn-outline btn-sm">
                    <Icon name="upload" size={15} /> Choisir une image
                    <input
                      type="file"
                      accept="image/png,image/jpeg,image/webp,image/gif"
                      hidden
                      onChange={(e) => onPickImage(e.target.files?.[0] ?? null)}
                    />
                  </label>
                  {(editor.product?.imageUrl || imageFile) && (
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => {
                        setImageFile(null)
                        setRemoveImage(true)
                      }}
                    >
                      <Icon name="trash" size={15} /> Retirer l'image
                    </button>
                  )}
                  <span className="muted xs">PNG, JPEG, WEBP ou GIF — 5 Mo maximum.</span>
                </div>
              </div>
              {saving && uploadPct > 0 && (
                <div
                  className="upload-bar"
                  role="progressbar"
                  aria-valuenow={uploadPct}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <span style={{ width: `${uploadPct}%` }} />
                </div>
              )}
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={toDelete !== null}
        title="Supprimer le produit"
        danger
        loading={deleting}
        confirmLabel="Supprimer"
        message={
          toDelete ? (
            <>
              Supprimer définitivement « <strong>{toDelete.name}</strong> » ? Cette action est irréversible.
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
