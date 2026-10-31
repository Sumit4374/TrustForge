import { AlertCircle, ArrowUpRight, LoaderCircle, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react'
export function Brand({ small = false }: { small?: boolean }) { return <Link to="/" className={`brand ${small ? 'small' : ''}`}><span className="brand-mark"><ShieldCheck size={small ? 19 : 25} strokeWidth={1.8} /></span><span>Trust<span className="brand-light">Forge</span><span className="brand-dot">.</span></span></Link> }
export function PageHead({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) { return <div className="page-head"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{description && <p className="muted page-description">{description}</p>}</div>{action}</div> }
export function Status({ value }: { value: string }) { return <span className={`status status-${value?.toLowerCase()}`}>{value?.replaceAll('_', ' ') || 'Unknown'}</span> }
export function ErrorBox({ error, retry }: { error: unknown; retry?: () => void }) { if (!error) return null; return <div role="alert" className="error-box"><AlertCircle size={19} /><span>{error instanceof Error ? error.message : String(error)}</span>{retry && <button className="button small ghost" onClick={retry}>Retry</button>}</div> }
export function Loading({ text = 'Loading your workspace…' }: { text?: string }) { return <div role="status" className="loading"><LoaderCircle className="spin" size={21} /><span>{text}</span><div className="skeleton" /><div className="skeleton short" /></div> }
export function Empty({ icon, title, description, action }: { icon?: ReactNode; title: string; description: string; action?: ReactNode }) { return <div className="empty"><div className="empty-icon">{icon || <ShieldCheck size={30} />}</div><h3>{title}</h3><p>{description}</p>{action}</div> }
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) { return <section className={`card ${className}`}>{children}</section> }
export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  const id = useId()
  const described = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined
  const control = isValidElement(children) ? cloneElement(children as ReactElement<Record<string, unknown>>, { 'aria-labelledby': `${id}-label`, 'aria-describedby': described, 'aria-invalid': error ? true : undefined }) : children
  return <label className="field"><span id={`${id}-label`}>{label}</span>{control}{hint && <small id={`${id}-hint`}>{hint}</small>}{error && <span id={`${id}-error`} role="alert" className="field-error">{error}</span>}</label>
}
export function Submit({ children, pending }: { children: ReactNode; pending?: boolean }) { return <button type="submit" className="button primary" disabled={pending}>{pending ? <LoaderCircle size={17} className="spin" /> : null}{children}</button> }
export function SectionLink({ to, children }: { to: string; children: ReactNode }) { return <Link className="text-link" to={to}>{children}<ArrowUpRight size={15} /></Link> }
