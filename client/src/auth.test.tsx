import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, ProtectedRoute } from './auth'
import { useAuth } from './session'
import { AuthPage } from './AuthPages'
import { ErrorBox } from './ui'
import type { ReactNode } from 'react'
function renderSession(children: ReactNode, path='/login') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><AuthProvider>{children}</AuthProvider></MemoryRouter></QueryClientProvider>)
  return client
}
beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path=String(input)
    if(path.endsWith('/auth/csrf')) return new Response(JSON.stringify({ token:'controlled-csrf',headerName:'X-CSRF-TOKEN' }))
    if(path.endsWith('/auth/me')) return new Response('{"detail":"Sign in"}',{status:401})
    if(path.endsWith('/auth/verify')) return new Response('{"message":"Verified"}')
    return new Response('{}')
  }))
})
describe('authentication forms and private routing', () => {
  it('redirects unauthenticated private deep links before rendering protected content', async () => {
    renderSession(<Routes><Route element={<ProtectedRoute />}><Route path="/projects/:id" element={<p>Private project</p>} /></Route><Route path="/login" element={<AuthPage mode="login" />} /></Routes>, '/projects/private-id')
    expect(await screen.findByRole('heading',{name:'Welcome back.'})).toBeInTheDocument()
    expect(screen.queryByText('Private project')).not.toBeInTheDocument()
  })
  it('validates a login form without sending credentials for invalid input', async () => {
    renderSession(<AuthPage mode="login" />)
    await userEvent.click(screen.getByRole('button',{name:/Sign in to workspace/}))
    expect(await screen.findByText('Enter your password.')).toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls.some(([url])=>String(url).endsWith('/auth/login'))).toBe(false)
  })
  it('reads single-use verification tokens from an email fragment', async () => {
    renderSession(<AuthPage mode="verify" />, '/verify-email#token=synthetic-email-token')
    expect(screen.getByRole('textbox',{name:/Email token/})).toHaveValue('synthetic-email-token')
    await userEvent.click(screen.getByRole('button',{name:'Verify email'}))
    expect(await screen.findByText('Your email is verified. You can now sign in.')).toBeInTheDocument()
  })
  it('opens a resent verification link on the same page with fresh form state', async () => {
    renderSession(<><Link to="/verify-email#token=synthetic-replacement-token">Open resent link</Link><AuthPage mode="verify" /></>, '/verify-email')
    await userEvent.type(screen.getByRole('textbox',{name:'Verification email address'}),'fixture@example.test')
    await userEvent.click(screen.getByRole('button',{name:'Resend verification email'}))
    expect(await screen.findByText('If the address is eligible, a verification email will arrive shortly.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link',{name:'Open resent link'}))
    expect(screen.getByRole('textbox',{name:/Email token/})).toHaveValue('synthetic-replacement-token')
    await userEvent.click(screen.getByRole('button',{name:'Verify email'}))
    expect(await screen.findByText('Your email is verified. You can now sign in.')).toBeInTheDocument()
    const submitted=vi.mocked(fetch).mock.calls.find(([url])=>String(url).endsWith('/auth/verify'))
    expect(JSON.parse(String(submitted?.[1]?.body))).toEqual({token:'synthetic-replacement-token'})
  })
  it('clears sensitive query data when the session expires', async () => {
    function State() { const { loading }=useAuth(); return <p>{loading?'Bootstrapping':'Ready'}</p> }
    const client=renderSession(<State />)
    await screen.findByText('Ready')
    client.setQueryData(['private-project'],{name:'Synthetic sensitive name'})
    window.dispatchEvent(new Event('session-expired'))
    await waitFor(()=>expect(client.getQueryData(['private-project'])).toBeUndefined())
  })
  it('renders hostile error text as text without interpreting HTML', () => {
    const payload='<img src=x onerror="window.bad=true">'
    const { container }=render(<ErrorBox error={new Error(payload)} />)
    expect(screen.getByRole('alert')).toHaveTextContent(payload)
    expect(container.querySelector('img')).toBeNull()
  })
})
