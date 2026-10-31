import { expect, test } from '@playwright/test'
import { createServer, type Server } from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve, sep, extname } from 'node:path'

type Fulfillment = { status?: number; contentType?: string; headers?: Record<string,string>; body?: string }
type FixtureRoute = { request: () => { url: () => string; method: () => string; headers: () => Record<string,string>; postDataJSON: () => Record<string,unknown> }; fulfill: (response: Fulfillment) => Promise<void> }
let fixtureServer: Server | undefined
test.afterEach(async () => {
  if (fixtureServer) {
    fixtureServer.closeAllConnections()
    await new Promise<void>((done,reject) => fixtureServer!.close(error => error ? reject(error) : done()))
    fixtureServer=undefined
  }
})

// Controlled transport fixtures exercise UI evidence handling; these are not live model results.
test('controlled evaluation fixture: final polling evidence, trace graph, advice, replay and export', async ({ page }) => {
  const projectId='00000000-0000-0000-0000-000000000001'
  const versionId='00000000-0000-0000-0000-000000000003'
  const candidateVersion='00000000-0000-0000-0000-000000000004'
  const baselineId='00000000-0000-0000-0000-000000000006'
  const candidateId='00000000-0000-0000-0000-000000000007'
  const scenarioId='00000000-0000-0000-0000-000000000005'
  const resultId='00000000-0000-0000-0000-000000000008'
  const createdAt='2026-10-31T06:00:00Z'
  const baseline={id:baselineId,projectId,agentVersionId:versionId,state:'COMPLETED',scenarioCount:1,completedCount:1,passedCount:0,failedCount:1,inconclusiveCount:0,preventedCalls:1,legitimateTasks:1,durationMs:10,createdAt}
  const candidate={...baseline,id:candidateId,agentVersionId:candidateVersion,passedCount:1,failedCount:0,preventedCalls:0}
  let statusReads=0
  let finalEvidenceReads=0
  let submittedKey=''
  const errors: string[]=[]
  page.on('pageerror',error=>errors.push(error.message))
  const handleApi = async (route: FixtureRoute) => {
    const request=route.request()
    const path=new URL(request.url()).pathname.replace('/api/v1','')
    const method=request.method()
    const fulfill=(body:unknown)=>route.fulfill({contentType:'application/json',body:JSON.stringify(body)})
    if(path==='/auth/me')return fulfill({id:'fixture-user',name:'Fixture Builder',email:'fixture@example.test',role:'USER'})
    if(path==='/auth/csrf')return fulfill({headerName:'X-CSRF-TOKEN',token:'controlled-fixture-csrf'})
    if(path===`/projects/${projectId}`)return fulfill({id:projectId,name:'Controlled fixture project',role:'OWNER',version:0,description:'Synthetic browser fixtures',createdAt})
    if(path===`/projects/${projectId}/agents`){
      const version=(id:string,name:string)=>({id,agentId:id,projectId,version:1,name,instructions:'Synthetic fixture only',allowedTools:['email.read','email.classify'],allowedRecipients:[],allowSend:false,maxSteps:3,timeoutSeconds:30,model:'qwen-3.8-27b'})
      return fulfill([{id:versionId,projectId,name:'Fixture baseline',versions:[version(versionId,'Fixture baseline')]},{id:candidateVersion,projectId,name:'Fixture candidate',versions:[version(candidateVersion,'Fixture candidate')]}])
    }
    if(path===`/projects/${projectId}/scenarios`)return fulfill({items:[{id:scenarioId,projectId,title:'Fixture prompt boundary',category:'PROMPT_INJECTION',description:'Controlled fixture',legitimateTask:'Classify synthetic inbox',adversarialInput:'Untrusted synthetic instructions',expectedOutputContains:'CLASSIFIED',approved:true,source:'MANUAL',createdAt}],total:1,page:0,size:20})
    if(path===`/projects/${projectId}/evaluations`&&method==='POST'){
      const body=request.postDataJSON()
      expect(body.agentVersionId).toBe(versionId)
      expect(body.scenarioIds).toEqual([scenarioId])
      expect(request.headers()['x-csrf-token']).toBe('controlled-fixture-csrf')
      submittedKey=body.idempotencyKey
      expect(submittedKey).toMatch(/^[0-9a-f-]{36}$/)
      return fulfill({...baseline,state:'QUEUED',completedCount:0})
    }
    if(path===`/evaluations/${baselineId}`){
      statusReads++
      return fulfill(statusReads===1?{...baseline,state:'RUNNING',completedCount:0,failedCount:0}:baseline)
    }
    if(path===`/evaluations/${candidateId}`)return fulfill(candidate)
    if(path.endsWith('/results')){
      if(path.includes(baselineId)&&statusReads===1)return fulfill({items:[],total:0,page:0,size:10})
      finalEvidenceReads++
      return fulfill({items:[{id:resultId,scenarioId,title:'Fixture prompt boundary',category:'PROMPT_INJECTION',outcome:path.includes(candidateId)?'PASSED':'FAILED',output:'CLASSIFIED: synthetic fixture',assertions:[{name:'UNAUTHORIZED_TOOL_CALL_ATTEMPTED',passed:path.includes(candidateId),detail:path.includes(candidateId)?'Fixture: prohibited action independently avoided.':'Fixture: a prohibited action was proposed.'},{name:'TOOL_EXECUTION_DENIED',passed:true,detail:'Fixture: the policy prevented execution.'}],preventedCalls:path.includes(candidateId)?0:1,unauthorizedAttempts:path.includes(candidateId)?0:1,secretExposed:false,legitimateTaskCompleted:true,durationMs:10}],total:1,page:0,size:10})
    }
    if(path.endsWith('/trace'))return fulfill({items:[{id:'event-1',resultId,step:1,event:'TOOL_PROPOSED',detail:'Controlled email.send proposal',createdAt},{id:'event-2',resultId,step:1,event:'TOOL_EXECUTION_DENIED',detail:'Fixture: no simulated send executed',createdAt}],total:2,page:0,size:25})
    if(path===`/reports/${baselineId}/recommendations`)return fulfill({id:'fixture-advice',source:'BHARATCODE',text:'Controlled fixture advice: narrow the allowlist.',createdAt})
    if(path===`/evaluations/${baselineId}/replay`){
      expect(request.postDataJSON().agentVersionId).toBe(candidateVersion)
      expect(request.postDataJSON().idempotencyKey).not.toBe(submittedKey)
      return fulfill(candidate)
    }
    if(path===`/reports/${candidateId}/export`)return route.fulfill({contentType:'text/markdown',headers:{'Content-Disposition':'attachment; filename="controlled-fixture.md"'},body:'# Controlled test fixture report'})
    if(path==='/dashboard')return fulfill({})
    return route.fulfill({status:404,body:'{"detail":"Unexpected controlled-fixture API route"}'})
  }
  // Downloads originate in Chromium's browser process and bypass page-route mocks.
  // A loopback-only test server serves the compiled SPA and all controlled API fixtures,
  // so the unchanged export URL reaches an actual HTTP response with attachment headers.
  const dist=resolve('dist')
  fixtureServer=createServer(async (request,response) => {
    try {
      const url=new URL(request.url || '/',`http://${request.headers.host}`)
      if (url.pathname.startsWith('/api/v1/')) {
        const chunks: Buffer[]=[]
        for await (const chunk of request) chunks.push(Buffer.from(chunk))
        const body=Buffer.concat(chunks).toString()
        await handleApi({
          request:()=>({url:()=>url.href,method:()=>request.method || 'GET',headers:()=>Object.fromEntries(Object.entries(request.headers).map(([key,value])=>[key,Array.isArray(value)?value.join(','):value || ''])),postDataJSON:()=>JSON.parse(body)}),
          fulfill:async value=>{response.statusCode=value.status || 200;if(value.contentType)response.setHeader('Content-Type',value.contentType);for(const [name,value_] of Object.entries(value.headers || {}))response.setHeader(name,value_);response.end(value.body || '')}
        })
        return
      }
      let file=resolve(dist,'.'+decodeURIComponent(url.pathname))
      if(!file.startsWith(dist+sep)&&file!==dist){response.writeHead(404).end();return}
      const types: Record<string,string>={'.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.html':'text/html'}
      let content: Buffer
      try{content=await readFile(file)}catch{file=resolve(dist,'index.html');content=await readFile(file)}
      response.setHeader('Content-Type',types[extname(file)] || 'application/octet-stream')
      response.end(content)
    } catch(error) {errors.push(String(error));response.writeHead(500).end('Controlled fixture server failed')}
  })
  await new Promise<void>(done=>fixtureServer!.listen(0,'127.0.0.1',done))
  const address=fixtureServer.address()
  if(!address || typeof address==='string')throw new Error('Controlled fixture server did not start')
  await page.goto(`http://127.0.0.1:${address.port}/projects/${projectId}/evaluations/new`)
  await page.locator('label.version-option').filter({hasText:'Fixture baseline'}).click()
  await page.getByRole('button',{name:'Continue',exact:true}).click()
  await page.getByRole('checkbox').check()
  await page.getByRole('button',{name:'Continue',exact:true}).click()
  await page.getByRole('button',{name:'Start evaluation',exact:true}).click()
  await expect(page).toHaveURL(new RegExp(`/evaluations/${baselineId}$`))
  await expect(page.getByText('COMPLETED',{exact:true})).toBeVisible({timeout:15000})
  await page.getByRole('button',{name:/Fixture prompt boundary/}).click()
  await expect(page.getByText('Fixture: a prohibited action was proposed.')).toBeVisible()
  await expect(page.getByText('Fixture: the policy prevented execution.')).toBeVisible()
  expect(finalEvidenceReads).toBeGreaterThan(0)
  await page.getByRole('button',{name:'Inspect execution trace'}).click()
  await expect(page.getByText('Fixture: no simulated send executed',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Graph',exact:true}).click()
  await expect(page.locator('.react-flow')).toBeVisible()
  await page.getByRole('tab',{name:'Remediate & rerun'}).click()
  await page.getByRole('button',{name:'Generate recommendations'}).click()
  await expect(page.getByText('Controlled fixture advice: narrow the allowlist.')).toBeVisible()
  await page.getByLabel('Candidate agent version').selectOption(candidateVersion)
  await page.getByRole('button',{name:'Rerun saved scenarios'}).click()
  await expect(page).toHaveURL(new RegExp(`/evaluations/${candidateId}$`))
  await expect(page.getByRole('tab',{name:'Results & assertions'})).toHaveAttribute('aria-selected','true')
  await page.getByRole('tab',{name:'Remediate & rerun'}).click()
  await expect(page.getByText('Controlled fixture advice: narrow the allowlist.')).toHaveCount(0)
  await expect(page.getByLabel('Candidate agent version')).toHaveValue('')
  const download=page.waitForEvent('download')
  await page.getByRole('link',{name:'Export report',exact:true}).click()
  expect((await download).suggestedFilename()).toBe('controlled-fixture.md')
  expect(errors).toEqual([])
})
