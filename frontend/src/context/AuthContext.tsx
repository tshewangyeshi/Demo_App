import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { getMe, login as apiLogin, logout as apiLogout, register as apiRegister, tryRestoreSession } from '../api/auth'
import { subscribeToAccessToken } from '../api/tokenStore'
import type { MeResponse } from '../api/types'

interface AuthContextValue {
  isAuthenticated: boolean
  isLoading: boolean
  user: MeResponse | null
  login: (email: string, password: string) => Promise<MeResponse>
  register: (email: string, password: string, firstName: string, lastName: string) => Promise<MeResponse>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [user, setUser] = useState<MeResponse | null>(null)

  useEffect(() => {
    return subscribeToAccessToken((token) => {
      setIsAuthenticated(token !== null)
      if (token === null) setUser(null)
    })
  }, [])

  useEffect(() => {
    // On first load, the only thing that survives a refresh is the httpOnly
    // cookie — try to silently re-derive an access token from it, then fetch
    // the profile (role/department) that role-based routing needs.
    tryRestoreSession()
      .then((restored) => (restored ? getMe().then(setUser).catch(() => setUser(null)) : undefined))
      .finally(() => setIsLoading(false))
  }, [])

  async function login(email: string, password: string) {
    await apiLogin(email, password)
    const me = await getMe()
    setUser(me)
    return me
  }

  async function register(email: string, password: string, firstName: string, lastName: string) {
    await apiRegister(email, password, firstName, lastName)
    const me = await getMe()
    setUser(me)
    return me
  }

  async function logout() {
    await apiLogout()
    setUser(null)
  }

  const value: AuthContextValue = { isAuthenticated, isLoading, user, login, register, logout }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
