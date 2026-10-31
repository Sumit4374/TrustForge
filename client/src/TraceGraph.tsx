import { readable } from './format'
import { Background, Controls, ReactFlow, type Edge, type Node } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import type { TraceStep } from './types'
import {  } from './ui'
export default function TraceGraph({ steps }: { steps: TraceStep[] }) {
  const nodes: Node[] = steps.map((step, index) => ({ id: step.id, position: { x: index % 2 * 45, y: index * 115 }, data: { label: `${index + 1}. ${readable(step.event)}\n${step.detail.slice(0, 95)}` }, style: { background: '#171f25', color: '#dce5e7', border: `1px solid ${step.event.includes('DENIED') || step.event.includes('VIOLATION') ? '#9c615d' : '#3d6961'}`, width: 310, fontSize: 12, padding: 14, borderRadius: 9 } }))
  const edges: Edge[] = steps.slice(1).map((step, i) => ({ id: `e-${step.id}`, source: steps[i].id, target: step.id, style: { stroke: '#52766e' } }))
  return <div className="trace-graph" aria-label="Execution event graph"><ReactFlow nodes={nodes} edges={edges} fitView minZoom={0.2} maxZoom={1.5} nodesDraggable={false} nodesConnectable={false} colorMode="dark" aria-label="Recorded execution flow"><Background gap={24} color="#2c373e" /><Controls showInteractive={false} /></ReactFlow></div>
}
