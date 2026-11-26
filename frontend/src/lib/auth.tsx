import React, { createContext, useContext, useEffect, useState } from 'react';
import { apiFetch, clearTokens, getStoredTokens, storeTokens } from './api';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  merchantId: string | null;
  roles: string[];
  active: boolean;
  demoUser: boolean;
}

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserProfile;
}

interface AuthContextValue {
  user: UserProfile | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const { accessToken } = getStoredTokens();
    if (!accessToken) {
      setLoading(false);
      return;
    }
    apiFetch<UserProfile>('/api/v1/auth/me')
      .then((profile) => setUser(profile))
      .catch(() => clearTokens())
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const res = await apiFetch<TokenResponse>('/api/v1/auth/login', {
      method: 'POST',
      skipAuth: true,
      body: JSON.stringify({ email, password }),
    });
    storeTokens(res.accessToken, res.refreshToken);
    setUser(res.user);
  };

  const logout = async () => {
    try {
      await apiFetch<void>('/api/v1/auth/logout', { method: 'POST' });
    } catch {
      // ignore
    } finally {
      clearTokens();
      setUser(null);
    }
  };

  const hasRole = (...roles: string[]) => {
    if (!user) return false;
    return roles.some((r) => user.roles.includes(r));
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, hasRole }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
