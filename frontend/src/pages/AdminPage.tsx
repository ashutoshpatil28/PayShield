import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
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

export function AdminPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('PLATFORM_ADMIN');

  const { data: users } = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => apiFetch<UserSummary[]>('/api/v1/admin/users'),
    enabled: isAdmin,
  });

  const { data: merchants } = useQuery({
    queryKey: ['merchants'],
    queryFn: () => apiFetch<Merchant[]>('/api/v1/merchants'),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Administration & Access Control</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Merchants, RBAC roles, and local demonstration accounts
        </p>
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
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
              {users?.map((u) => (
                <tr key={u.id}>
                  <td className="px-4 py-3 font-medium">{u.fullName}</td>
                  <td className="px-4 py-3 font-mono text-xs">{u.email}</td>
                  <td className="px-4 py-3 font-mono text-xs">{u.roles.join(', ')}</td>
                  <td className="px-4 py-3"><StatusBadge value={u.active ? 'ACTIVE' : 'DISABLED'} /></td>
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
    </div>
  );
}
