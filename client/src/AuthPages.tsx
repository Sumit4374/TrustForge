import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowLeft, ArrowUpRight, CheckCircle2, LockKeyhole } from 'lucide-react'
import { post, safeReturnPath } from './api'
import { useAuth } from './session'
import { Brand, ErrorBox, Field, Submit } from './ui'
const schema = z.object({ email: z.email('Enter a valid email address.'), password: z.string().min(1, 'Enter your password.'), name: z.string() })
type Values = z.infer<typeof schema>
type AuthMode = 'login' | 'register' | 'forgot' | 'reset' | 'verify'
export function AuthPage({ mode }: { mode: AuthMode }) {
  const location = useLocation()
  return <AuthForm key={location.pathname + location.search + location.hash} mode={mode} />
}
function AuthForm({ mode }: { mode: AuthMode }) {
  const location = useLocation()
  const [params] = useSearchParams()
  const [token] = useState(params.get('token') || new URLSearchParams(location.hash.slice(1)).get('token') || '')
  useEffect(() => {
    const url = new URL(window.location.href)
    if (url.searchParams.has('token') || new URLSearchParams(url.hash.slice(1)).has('token')) {
      url.searchParams.delete('token'); url.hash = ''
      window.history.replaceState(window.history.state, '', url.pathname + url.search)
    }
  }, [])
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '', name: '' } })
  const title = { login: 'Welcome back.', register: 'Build with confidence.', forgot: 'Reset your password.', reset: 'Choose a new password.', verify: 'Verify your email.' }[mode]
  const submit = async (values: Values) => {
    setError(undefined)
    try {
      if (mode === 'login') { await login(values.email, values.password); navigate(safeReturnPath(params.get('returnTo')), { replace: true }) }
      else { if (values.password.length < 12) throw new Error('Use a password with at least 12 characters.'); if (!values.name.trim()) throw new Error('Enter your name.'); await post('/auth/register', values); setMessage('If registration is available, a verification email will arrive shortly. Check your inbox, then sign in after verification.') }
    } catch (e) { setError(e) }
  }
  const tokenAction = async (form: HTMLFormElement) => {
    setBusy(true); setError(undefined)
    const data = new FormData(form)
    try {
      if (mode === 'verify') { await post('/auth/verify', { token: data.get('token') }); setMessage('Your email is verified. You can now sign in.') }
      if (mode === 'forgot') { await post('/auth/forgot-password', { email: data.get('email') }); setMessage('If an account is eligible, a password reset email will arrive shortly.') }
      if (mode === 'reset') { await post('/auth/reset-password', { token: data.get('token'), password: data.get('password') }); setMessage('Your password has been updated and previous sessions revoked. Sign in with your new password.') }
    } catch (e) { setError(e) } finally { setBusy(false) }
  }
  const resend = async (form: HTMLFormElement) => {
    setBusy(true); setError(undefined)
    try { await post('/auth/resend-verification', { email: new FormData(form).get('email') }); setMessage('If the address is eligible, a verification email will arrive shortly.') }
    catch (e) { setError(e) } finally { setBusy(false) }
  }
  return <div className="auth-page"><aside className="auth-story"><Brand /><div><p className="eyebrow">THE RELIABILITY LAYER FOR AI AGENTS</p><h1>Capability is a start.<br /><span>Trust is earned.</span></h1><p>Put your agents through controlled adversarial scenarios. Understand the behavior. Keep the evidence.</p><div className="auth-terminal"><div><span className="terminal-dot" /><span className="terminal-dot" /><span className="terminal-dot" /><span>trustforge / evaluation</span></div><p><span className="teal">01</span> Define expected behavior</p><p><span className="teal">02</span> Challenge the boundaries</p><p><span className="teal">03</span> Inspect what actually happened</p><p className="muted">Every finding starts with evidence.</p></div></div><span className="fine-print">Synthetic environments. Real agent behavior.</span></aside><main className="auth-main"><Link className="back-link" to="/"><ArrowLeft size={16} />Back to TrustForge</Link><div className="auth-form"><div className="auth-icon"><LockKeyhole size={22} /></div><p className="eyebrow">YOUR TRUSTFORGE WORKSPACE</p><h2>{title}</h2><p className="muted">{mode === 'login' ? 'Sign in to continue evaluating your agents.' : mode === 'register' ? 'Create your account. Verify your email. Start testing.' : 'Secure access, with a single-use email token.'}</p><ErrorBox error={error} />{message ? <div className="success-box"><CheckCircle2 size={22} /><p>{message}</p><Link className="button primary" to="/login">Continue to sign in<ArrowUpRight size={16} /></Link></div> : mode === 'login' || mode === 'register' ? <form onSubmit={handleSubmit(submit)}>{mode === 'register' && <Field label="Full name" error={errors.name?.message}><input autoComplete="name" {...register('name')} /></Field>}<Field label="Email address" error={errors.email?.message}><input type="email" autoComplete="email" placeholder="you@company.com" {...register('email')} /></Field><Field label="Password" error={errors.password?.message} hint={mode === 'register' ? 'At least 12 characters. A long, unique phrase works well.' : undefined}><input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} {...register('password')} /></Field>{mode === 'login' && <Link className="text-link forgot-link" to="/forgot-password">Forgot password?</Link>}<Submit pending={isSubmitting}>{mode === 'login' ? 'Sign in to workspace' : 'Create account'}<ArrowUpRight size={17} /></Submit></form> : <form onSubmit={e => { e.preventDefault(); void tokenAction(e.currentTarget) }}>{mode === 'forgot' ? <Field label="Email address"><input required type="email" name="email" autoComplete="email" /></Field> : <Field label="Email token" hint="Paste the single-use token from your verification email."><input required name="token" defaultValue={token} autoComplete="off" /></Field>}{mode === 'reset' && <Field label="New password" hint="At least 12 characters."><input required minLength={12} maxLength={128} type="password" name="password" autoComplete="new-password" /></Field>}<Submit pending={busy}>{mode === 'verify' ? 'Verify email' : mode === 'forgot' ? 'Send reset email' : 'Reset password'}</Submit></form>}{mode === 'verify' && !message && <form onSubmit={e => { e.preventDefault(); void resend(e.currentTarget) }}><h3>Need a new verification email?</h3><Field label="Verification email address"><input type="email" name="email" autoComplete="email" maxLength={254} required /></Field><Submit pending={busy}>Resend verification email</Submit></form>}<p className="auth-switch">{mode === 'login' ? <>New to TrustForge? <Link to="/register">Create an account</Link> · <Link to="/verify-email">Verify email</Link></> : <Link to="/login">Return to sign in</Link>}</p><p className="secure-note"><LockKeyhole size={13} />Your session stays on the server.</p></div></main></div>
}
