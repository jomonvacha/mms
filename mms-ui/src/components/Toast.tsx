import toast, { type Toast, resolveValue } from 'react-hot-toast'
import { CheckCircle2, AlertCircle, AlertTriangle, Info, X, Loader2 } from 'lucide-react'

type Variant = 'success' | 'error' | 'warning' | 'info' | 'loading'

interface ThemedToastProps {
  t: Toast
  variant: Variant
  title: React.ReactNode
  description?: React.ReactNode
  /** Optional inline action. Click auto-dismisses. */
  action?: { label: string; onClick: () => void }
  /** Render a dismiss X button (default true for non-loading). */
  dismissible?: boolean
}

const VARIANT_TOKENS: Record<Variant, {
  icon: React.ElementType
  accent: string
  iconColor: string
  iconBg: string
}> = {
  success: {
    icon: CheckCircle2,
    accent: 'bg-emerald-500',
    iconColor: 'text-emerald-600 dark:text-emerald-400',
    iconBg: 'bg-emerald-50 dark:bg-emerald-900/30',
  },
  error: {
    icon: AlertCircle,
    accent: 'bg-red-500',
    iconColor: 'text-red-600 dark:text-red-400',
    iconBg: 'bg-red-50 dark:bg-red-900/30',
  },
  warning: {
    icon: AlertTriangle,
    accent: 'bg-amber-500',
    iconColor: 'text-amber-600 dark:text-amber-400',
    iconBg: 'bg-amber-50 dark:bg-amber-900/30',
  },
  info: {
    icon: Info,
    accent: 'bg-brand-500',
    iconColor: 'text-brand-600 dark:text-brand-400',
    iconBg: 'bg-brand-50 dark:bg-brand-900/30',
  },
  loading: {
    icon: Loader2,
    accent: 'bg-gray-400 dark:bg-gray-500',
    iconColor: 'text-gray-600 dark:text-gray-300',
    iconBg: 'bg-gray-100 dark:bg-gray-800',
  },
}

function ThemedToast({ t, variant, title, description, action, dismissible = true }: ThemedToastProps) {
  const tok = VARIANT_TOKENS[variant]
  const Icon = tok.icon

  return (
    <div
      className={`relative flex w-[22rem] max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-1))] shadow-lg shadow-gray-900/5 dark:shadow-black/30
                  transition-all ${t.visible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-1'}`}
      role={variant === 'error' ? 'alert' : 'status'}
      aria-live={variant === 'error' ? 'assertive' : 'polite'}>
      <div className={`w-1 flex-shrink-0 ${tok.accent}`} />
      <div className="flex-1 flex items-start gap-3 p-3.5">
        <div className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center ${tok.iconBg}`}>
          <Icon size={16} className={`${tok.iconColor} ${variant === 'loading' ? 'animate-spin' : ''}`} />
        </div>
        <div className="flex-1 min-w-0 pt-0.5">
          <p className="text-sm font-medium text-[rgb(var(--text-primary))] leading-snug">{title}</p>
          {description && (
            <p className="text-xs text-[rgb(var(--text-muted))] mt-1 leading-relaxed">{description}</p>
          )}
          {action && (
            <button
              type="button"
              onClick={() => { action.onClick(); toast.dismiss(t.id) }}
              className="mt-2 text-xs font-semibold text-brand-600 dark:text-brand-400 hover:text-brand-700 dark:hover:text-brand-300 focus:outline-none focus:ring-2 focus:ring-brand-500/40 rounded">
              {action.label}
            </button>
          )}
        </div>
        {dismissible && variant !== 'loading' && (
          <button
            type="button"
            onClick={() => toast.dismiss(t.id)}
            className="flex-shrink-0 p-1 -m-1 rounded text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-primary))] focus:outline-none focus:ring-2 focus:ring-brand-500/40"
            aria-label="Dismiss notification">
            <X size={14} />
          </button>
        )}
      </div>
    </div>
  )
}

interface NotifyOptions {
  description?: React.ReactNode
  action?: { label: string; onClick: () => void }
  duration?: number
  id?: string
}

function render(variant: Variant, title: React.ReactNode, opts: NotifyOptions = {}) {
  return toast.custom((t) => (
    <ThemedToast t={t} variant={variant} title={title} description={opts.description} action={opts.action} />
  ), {
    duration: opts.duration ?? (variant === 'error' ? 6000 : 4000),
    id: opts.id,
  })
}

/** Themed toast helpers — drop-in replacement for the raw react-hot-toast API.
 *  Existing `toast.success(...)` / `toast.error(...)` calls still render, but are
 *  re-skinned via the global Toaster config (see main.tsx). Prefer `notify.*`
 *  for new code since it supports description + action. */
export const notify = {
  success: (title: React.ReactNode, opts?: NotifyOptions) => render('success', title, opts),
  error:   (title: React.ReactNode, opts?: NotifyOptions) => render('error',   title, opts),
  warning: (title: React.ReactNode, opts?: NotifyOptions) => render('warning', title, opts),
  info:    (title: React.ReactNode, opts?: NotifyOptions) => render('info',    title, opts),
  loading: (title: React.ReactNode, opts?: NotifyOptions) => render('loading', title, opts),
  dismiss: (id?: string) => toast.dismiss(id),
}

/** Default-variant renderer used by the global Toaster config to re-skin the
 *  built-in toast.success / toast.error calls. Keeps legacy call sites looking
 *  native in the new theme. */
export function legacyToastRenderer(t: Toast) {
  const title = resolveValue(t.message, t)
  const variant: Variant =
    t.type === 'success' ? 'success' :
    t.type === 'error'   ? 'error'   :
    t.type === 'loading' ? 'loading' : 'info'
  return <ThemedToast t={t} variant={variant} title={title} />
}
