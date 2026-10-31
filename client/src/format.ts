export const readable = (text: string) => text?.replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase())
export const date = (value?: string) => value ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
