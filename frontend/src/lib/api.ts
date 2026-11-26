export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  errorCode: string;
  message: string;
  correlationId?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  status: number;
  errorCode: string;
  correlationId?: string;
  fieldErrors?: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message || 'API request failed');
    this.status = body.status;
    this.errorCode = body.errorCode;
    this.correlationId = body.correlationId;
    this.fieldErrors = body.fieldErrors;
  }
}

const ACCESS_KEY = 'payshield_access_token';
const REFRESH_KEY = 'payshield_refresh_token';

export function getStoredTokens() {
  return {
    accessToken: localStorage.getItem(ACCESS_KEY),
    refreshToken: localStorage.getItem(REFRESH_KEY),
  };
}

export function storeTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

let refreshPromise: Promise<string | null> | null = null;

async function tryRefreshToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;
  const { refreshToken } = getStoredTokens();
  if (!refreshToken) return null;

  refreshPromise = (async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) {
        clearTokens();
        return null;
      }
      const data = await res.json();
      storeTokens(data.accessToken, data.refreshToken);
      return data.accessToken as string;
    } catch {
      clearTokens();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit & { skipAuth?: boolean } = {}
): Promise<T> {
  const { skipAuth, headers, ...rest } = options;
  const { accessToken } = getStoredTokens();

  const buildHeaders = (token: string | null) => {
    const h = new Headers(headers);
    if (!h.has('Content-Type') && !(rest.body instanceof FormData)) {
      h.set('Content-Type', 'application/json');
    }
    if (!skipAuth && token) {
      h.set('Authorization', `Bearer ${token}`);
    }
    return h;
  };

  let response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: buildHeaders(accessToken),
  });

  if (response.status === 401 && !skipAuth) {
    const newAccess = await tryRefreshToken();
    if (newAccess) {
      response = await fetch(`${API_BASE_URL}${path}`, {
        ...rest,
        headers: buildHeaders(newAccess),
      });
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const parsed = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(
      parsed || {
        timestamp: new Date().toISOString(),
        status: response.status,
        errorCode: 'HTTP_ERROR',
        message: response.statusText,
      }
    );
  }

  return parsed as T;
}
