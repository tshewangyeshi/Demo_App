import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { login as apiLogin, logout as apiLogout, register as apiRegister, tryRestoreSession } from '../api/auth'
import { subscribeToAccessToken } from '../api/tokenStore'

interface AuthContextValue {
  isAuthenticated: boolean
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, firstName: string, lastName: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    return subscribeToAccessToken((token) => setIsAuthenticated(token !== null))
  }, [])

  useEffect(() => {
    // On first load, the only thing that survives a refresh is the httpOnly
    // cookie — try to silently re-derive an access token from it.
    tryRestoreSession().finally(() => setIsLoading(false))
  }, [])

  const value: AuthContextValue = {
    isAuthenticated,
    isLoading,
    login: apiLogin,
    register: apiRegister,
    logout: apiLogout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
