import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { Card, ErrorBox, Loading } from './ui'
import type { BenchmarkSummary } from './types'

import { rateText } from './benchmarkRates'
const labels: Record<string,string> = { executedAttackSuccessRate:'Executed attack success', blockedAttemptTrialRate:'Blocked unsafe attempt trials', otherBehaviorFailureRate:'Other behavior failures', legitimateTaskSuccessRate:'Legitimate task success', policyPreventionRate:'Policy prevention', inconclusiveRate:'Inconclusive trials' }
export function BenchmarkPanel({ evaluationId, refresh = false }: { evaluationId?: string; refresh?: boolean }) {
  const query=useQuery({queryKey:['benchmarks',evaluationId || 'dashboard'],queryFn:()=>api<BenchmarkSummary>(evaluationId?`/reports/${evaluationId}/benchmarks`:'/dashboard/benchmarks'),refetchInterval:refresh?2000:false})
  const data=query.data
  return <Card className="form-card benchmark-panel"><h3>Observed reliability metrics</h3><ErrorBox error={query.error} retry={()=>void query.refetch()} />{query.isPending?<Loading text="Loading persisted benchmark observations…" />:data?.rates&&<>
    <p className="muted">{data.observedExecutions} recorded / {data.plannedExecutions} planned trial executions · {data.missingEvidence} historical results without structured evidence.</p>
    {data.providers.includes('CONTROLLED_TEST_FIXTURE')&&<p className="warning-box">Controlled test provider: these observations verify the harness, not a real-model security benchmark. Token usage is unavailable unless measured by a real adapter.</p>}
    <div className="table-scroll"><table><thead><tr><th>Measurement</th><th>Observed fraction</th></tr></thead><tbody>{Object.entries(data.rates).map(([name,rate])=><tr key={name}><td title={rate.definition}>{labels[name] || name}</td><td className="mono">{rateText(rate)}</td></tr>)}</tbody></table></div>
    <details><summary>Definitions, sample scope and provider coverage</summary><p className="muted">{data.scope}</p><ul>{Object.entries(data.rates).map(([name,rate])=><li key={name}><b>{labels[name] || name}:</b> {rate.definition}</li>)}</ul>
      <p>Logical decision attempts: {data.providerUsage.decisionAttempts}. Known external transport attempts: {data.providerUsage.knownTransportAttempts ?? 'unknown'} ({data.providerUsage.unknownTransportCalls} calls with unknown transport coverage).</p>
      <p>Known input/output tokens: {data.providerUsage.knownUsageCalls ? `${data.providerUsage.knownInputTokens}/${data.providerUsage.knownOutputTokens}` : 'unavailable'} · Usage coverage: {data.providerUsage.knownUsageCalls}/{data.providerUsage.knownUsageCalls+data.providerUsage.unknownUsageCalls} calls. Recorded decision time: {data.providerUsage.summedDecisionDurationMs}ms · Provider/format errors: {data.providerUsage.providerErrors}.</p>
      <p className="muted">{data.uncertaintyNotice}</p>
    </details>
  </>}</Card>
}
