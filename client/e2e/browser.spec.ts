import { expect, test } from '@playwright/test'
// These are explicitly controlled browser fixtures, not BharatCode inference or backend tests.
const user={id:'fixture-user',email:'fixture@example.test',name:'Fixture Builder',role:'USER'}
test('public landing and mobile navigation remain usable',async({page})=>{
  await page.route('**/api/v1/auth/me',route=>route.fulfill({status:401,body:'{}'}))
  await page.goto('/')
  await expect(page.getByRole('heading',{name:/Build agents/})).toBeVisible()
  await expect(page.getByText('ILLUSTRATIVE WORKFLOW')).toBeVisible()
  await page.screenshot({path:'/tmp/trustforge-landing-desktop.png',fullPage:true,animations:'disabled'})
  await page.setViewportSize({width:390,height:844})
  await expect(page.getByRole('link',{name:'Create your workspace'})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true)
})
test('direct private URL and refresh require authentication',async({page})=>{
  await page.route('**/api/v1/**',route=>route.fulfill({status:401,contentType:'application/problem+json',body:'{"detail":"Sign in"}'}))
  await page.goto('/projects/unauthorized-id')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  await expect(page.getByRole('heading',{name:'Welcome back.'})).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading',{name:'Welcome back.'})).toBeVisible()
})
test('authorized empty dashboard renders no fictional data and stores no credentials',async({page})=>{
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname
    const body=path.endsWith('/auth/me')?user:path.endsWith('/dashboard')?{projects:0,totalEvaluations:0,completedEvaluations:0,passed:0,failed:0,inconclusive:0,preventedCalls:0,legitimateTasks:0,recentRuns:[],categoryBreakdown:[],providerConfigured:false}:{}
    return route.fulfill({contentType:'application/json',body:JSON.stringify(body)})
  })
  await page.goto('/dashboard')
  await expect(page.getByRole('heading',{name:'Welcome back, Fixture.'})).toBeVisible()
  await expect(page.getByText("No evidence yet. Let's change that.")).toBeVisible()
  await expect(page.getByText('Connect BharatCode to enable model-backed evaluations')).toBeVisible()
  await page.screenshot({path:'/tmp/trustforge-dashboard-desktop.png',fullPage:true,animations:'disabled'})
  expect(await page.evaluate(()=>({local:localStorage.length,session:sessionStorage.length}))).toEqual({local:0,session:0})
  await page.setViewportSize({width:390,height:844})
  await page.getByRole('button',{name:'Open navigation'}).click()
  await expect(page.getByRole('link',{name:'Scenario library',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Close navigation'}).click()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true)
})
test('noncomparable reports show no measured delta',async({page})=>{
  const run={id:'baseline-id',projectId:'project',agentVersionId:'v1',state:'COMPLETED',scenarioCount:1,completedCount:1,passedCount:0,failedCount:1,inconclusiveCount:0,preventedCalls:1,legitimateTasks:0,durationMs:10,createdAt:'2026-10-30T10:00:00Z'}
  await page.route('**/api/v1/**',route=>{
    const url=new URL(route.request().url())
    const body=url.pathname.endsWith('/auth/me')?user:url.pathname.endsWith('/evaluations/compare')?{baseline:run,candidate:{...run,id:'candidate-id'},comparable:false,reason:'Different fixtures',delta:null}:{items:[],total:0,page:0,size:20}
    return route.fulfill({contentType:'application/json',body:JSON.stringify(body)})
  })
  await page.goto('/compare?projectId=project&baselineId=baseline-id&candidateId=candidate-id')
  await expect(page.getByText(/These runs are not comparable/)).toBeVisible()
  await expect(page.getByRole('cell',{name:'—',exact:true})).toHaveCount(6)
})
