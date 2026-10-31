import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, post } from './api'
import { Card, ErrorBox, Field } from './ui'
import type { AgentVersion, Items, Run, TestResult } from './types'
type Configuration=Omit<AgentVersion,'id'|'agentId'|'projectId'|'version'>
type Proposal={id:string;resultId:string;baseVersionId:string;current:Configuration;proposed:Configuration;explanation:string;source:string;state:string;changes:{field:string;before:string;proposed:string}[];warnings:string[];newVersionId:string|null}
export function RemediationReview({evaluationId,editable,completed}:{evaluationId:string;editable:boolean;completed:boolean}){
  const client=useQueryClient(),navigate=useNavigate()
  const [result,setResult]=useState(''),[selected,setSelected]=useState(''),[ack,setAck]=useState(false)
  const failures=useQuery({queryKey:['remediation-failures',evaluationId],queryFn:()=>api<Items<TestResult>>(`/evaluations/${evaluationId}/results?size=100`)})
  const proposals=useQuery({queryKey:['proposals',evaluationId],queryFn:()=>api<Proposal[]>(`/reports/${evaluationId}/proposals`)})
  const proposal=proposals.data?.find(p=>p.id===selected)||proposals.data?.[0]
  const generate=useMutation({mutationFn:()=>post<Proposal>(`/reports/${evaluationId}/proposals`,{resultId:result}),onSuccess:p=>{setSelected(p.id);setAck(false);void client.invalidateQueries({queryKey:['proposals',evaluationId]})}})
  const review=useMutation({mutationFn:(approve:boolean)=>post<Proposal>(`/reports/${evaluationId}/proposals/${proposal?.id}/review`,{approve,acknowledged:ack}),onSuccess:()=>{setAck(false);void client.invalidateQueries({queryKey:['proposals',evaluationId]});void client.invalidateQueries({queryKey:['agents']})}})
  const replay=useMutation({mutationFn:()=>post<Run>(`/evaluations/${evaluationId}/replay`,{agentVersionId:proposal?.newVersionId,idempotencyKey:crypto.randomUUID()}),onSuccess:r=>navigate(`/evaluations/${r.id}`)})
  return <Card className="form-card"><h3>Review a proposed agent version</h3><p className="muted">Select a failed trial. Review every instruction and permission change before approving an immutable version. Replay remains a separate action.</p><ErrorBox error={failures.error||proposals.error||generate.error||review.error||replay.error}/>
    <Field label="Failed trial to remediate"><select value={result} disabled={!editable||!completed} onChange={e=>setResult(e.target.value)}><option value="">Choose a failed trial</option>{failures.data?.items.filter(r=>r.outcome==='FAILED').map(r=><option key={r.id} value={r.id}>{r.title} · Trial {r.trialIndex||1}</option>)}</select></Field>
    {editable&&<button className="button primary" disabled={!completed||!result||generate.isPending} onClick={()=>generate.mutate()}>{generate.isPending?'Generating proposal…':'Generate configuration proposal'}</button>}
    {!!proposals.data?.length&&<Field label="Saved proposal"><select value={proposal?.id||''} onChange={e=>{setSelected(e.target.value);setAck(false)}}>{proposals.data.map(p=><option key={p.id} value={p.id}>{p.id.slice(0,8)} · {p.state}</option>)}</select></Field>}
    {proposal&&<><p className="mono">{proposal.source} · {proposal.state} · Baseline version {proposal.baseVersionId.slice(0,8)}</p><p>{proposal.explanation}</p>{proposal.warnings.map(w=><p className="warning-box" key={w}>{w}</p>)}
      <details open><summary>Current and proposed agent configuration</summary><h4>Current configuration</h4><pre className="recommendation">{JSON.stringify(proposal.current,null,2)}</pre><h4>Proposed configuration</h4><pre className="recommendation">{JSON.stringify(proposal.proposed,null,2)}</pre></details>
      <h4>Changed fields and permissions</h4>{proposal.changes.length?proposal.changes.map(c=><div key={c.field}><strong>{c.field}</strong><p>Before</p><pre className="recommendation">{c.before}</pre><p>Proposed</p><pre className="recommendation">{c.proposed}</pre></div>):<p>No fields changed.</p>}
      {editable&&proposal.state==='PENDING'&&<><label className="checkbox-row"><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)}/>I reviewed the proposed instructions, permissions and execution limits.</label><div className="button-row"><button className="button primary" disabled={!ack||review.isPending} onClick={()=>review.mutate(true)}>Approve new agent version</button><button className="button secondary" disabled={review.isPending} onClick={()=>review.mutate(false)}>Reject proposal</button></div></>}
      {proposal.newVersionId&&<><p>Created immutable agent version: <span className="mono">{proposal.newVersionId}</span></p>{editable&&<button className="button primary" disabled={replay.isPending} onClick={()=>replay.mutate()}>Replay approved version</button>}</>}
    </>}
  </Card>
}
