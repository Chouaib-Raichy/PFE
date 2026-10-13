interface SpinnerProps {
  size?: number
  large?: boolean
  label?: string
}

export function Spinner({ size, large = false, label }: SpinnerProps) {
  const style = size ? { width: size, height: size } : undefined
  return (
    <span
      className={`spinner${large ? ' spinner-lg' : ''}`}
      role="status"
      aria-label={label ?? 'Chargement'}
      style={style}
    />
  )
}
