import { useEffect, useState } from 'react'
import { Icon } from './Icon'

interface ProductImageProps {
  src: string | null | undefined
  alt: string
  /** Taille de l'icone de repli affichee quand aucune image n'est disponible. */
  iconSize?: number
}

/**
 * Image produit avec repli robuste : aucune image OU echec de chargement
 * (stockage indisponible) affiche une icone neutre au lieu d'une image cassee.
 */
export function ProductImage({ src, alt, iconSize = 42 }: ProductImageProps) {
  const [failed, setFailed] = useState(false)

  useEffect(() => setFailed(false), [src])

  if (!src || failed) {
    return (
      <span className="placeholder">
        <Icon name="image" size={iconSize} />
      </span>
    )
  }

  return <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} />
}
