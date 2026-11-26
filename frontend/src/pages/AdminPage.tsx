import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRound, Plus } from 'lucide-react';
import { apiFetch, ApiError } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { useAuth } from '../lib/auth';

interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  active: boolean;
  demoUser: boolean;
}

interface Merchant {
  id: string;
  merchantCode: string;
  displayName: string;
  status: string;
  defaultCurrency: string;
  demoData: boolean;
}

const ALL_ROLES = [
  'PLATFORM_ADMIN',
  'MERCHANT_ADMIN',
  'OPERATIONS_ANALYST',
  'RISK_ANALYST',
  'PAYOUT_MAKER',
  'PAYOUT_CHECKER',
  'FINANCIAL_AUDITOR',
];

export function AdminPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const isAdmin = hasRole('PLATFORM_ADMIN');

  const [showCreateUser, setShowCreateUser] = useState(false);
  const [revokeUserId, setRevokeUserId] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('OPERATIONS_ANALYST');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { data: users } = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => apiFetch<UserSummary[]>('/api/v1/admin/users'),
    enabled: isAdmin,
  });

  const { data: merchants } = useQuery({
    queryKey: ['merchants'],
    queryFn: () => apiFetch<Merchant[]>('/api/v1/merchants'),
  });

  const createUser = useMutation({
    mutationFn: () =>
      apiFetch('/api/v1/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          email,
          fullName,
          password,
          roles: [role],
        }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      setShowCreateUser(false);
      setEmail('');
      setFullName('');
      setPassword('');
      setErrorMsg(null);
    },
    onError: (err) => setErrorMsg(err instanceof ApiError ? err.message : 'Failed to create user'),
  });

  const toggleStatus = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      apiFetch(`/api/v1/admin/users/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ active }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  const revokeSessions = useMutation({
    mutationFn: (id: string) =>
      apiFetch(`/api/v1/admin/users/${id}/revoke-sessions`, { method: 'POST' }),
    onSuccess: () => {
      setRevokeUserId(null);
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Administration & Access Control</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Merchants, RBAC roles, session revocation, and local demonstration accounts
          </p>
        </div>
        {isAdmin && (
          <button
            type="button"
            onClick={() => setShowCreateUser(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
          >
            <Plus className="w-4 h-4" /> Create User
          </button>
        )}
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold mb-3">Merchants</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {merchants?.map((m) => (
            <div key={m.id} className="p-3 rounded-lg border border-slate-200 dark:border-navy-700 flex items-center justify-between">
              <div>
                <div className="font-medium text-sm">{m.displayName}</div>
                <div className="text-xs font-mono text-slate-500">{m.merchantCode} • {m.defaultCurrency}</div>
              </div>
              <StatusBadge value={m.status} />
            </div>
          ))}
        </div>
      </div>

      {isAdmin ? (
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-x-auto">
          <div className="p-4 border-b border-slate-200 dark:border-navy-700 text-sm font-semibold">
            Platform Users & RBAC Roles
          </div>
          <table className="w-full text-sm text-left">
            <thead className="bg-slate-50 dark:bg-navy-800/60 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Roles</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
              {users?.map((u) => (
                <tr key={u.id}>
                  <td className="px-4 py-3 font-medium">{u.fullName}</td>
                  <td className="px-4 py-3 font-mono text-xs">{u.email}</td>
                  <td className="px-4 py-3 font-mono text-xs">{u.roles.join(', ')}</td>
                  <td className="px-4 py-3"><StatusBadge value={u.active ? 'ACTIVE' : 'DISABLED'} /></td>
                  <td className="px-4 py-3 text-right space-x-1.5">
                    <button
                      type="button"
                      onClick={() => toggleStatus.mutate({ id: u.id, active: !u.active })}
                      className="px-2.5 py-1 rounded border border-slate-300 dark:border-navy-700 text-xs hover:bg-slate-100 dark:hover:bg-navy-800"
                    >
                      {u.active ? 'Disable' : 'Enable'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setRevokeUserId(u.id)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded border border-amber-500/30 text-amber-500 text-xs hover:bg-amber-500/10"
                    >
                      <KeyRound className="w-3 h-3" /> Revoke Sessions
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/25 text-xs text-amber-500">
          User management is restricted to PLATFORM_ADMIN.
        </div>
      )}

      <ConfirmDialog
        open={!!revokeUserId}
        title="Revoke All Active Sessions?"
        description="All refresh tokens for this user will be immediately revoked and logged to the audit trail."
        confirmLabel="Revoke Sessions"
        destructive
        loading={revokeSessions.isPending}
        onConfirm={() => revokeUserId && revokeSessions.mutate(revokeUserId)}
        onCancel={() => setRevokeUserId(null)}
      />

      {showCreateUser && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4" role="dialog" aria-modal="true">
          <div className="w-full max-w-md bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-6 space-y-4">
            <h2 className="text-lg font-semibold">Create Platform User</h2>
            {errorMsg && <div className="p-2.5 rounded bg-rose-500/10 text-rose-400 text-xs">{errorMsg}</div>}
            <div className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-medium mb-1">Full Name</label>
                <input
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700"
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700"
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Initial Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700"
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Role</label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700"
                >
                  {ALL_ROLES.map((r) => (
                    <option key={r} value={r}>{r}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setShowCreateUser(false)} className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-navy-700 text-sm">
                Cancel
              </button>
              <button
                type="button"
                onClick={() => createUser.mutate()}
                className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
              >
                Create User
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
