import { createContext, useContext } from 'react'
import type { User } from './types'
export const AuthContext = createContext<{ user: User | null; loading: boolean; error: string; reload: (silent?: boolean) => Promise<void>; login: (email: string, password: string) => Promise<void>; logout: () => Promise<void> } | null>(null)
export function useAuth() { return useContext(AuthContext)! }
