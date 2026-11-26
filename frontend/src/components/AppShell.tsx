import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  ArrowLeftRight,
  BookOpen,
  FileCheck2,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Send,
  Server,
  Settings,
  Shield,
  ShieldCheck,
  Sun,
} from 'lucide-react';
import { useAuth } from '../lib/auth';

const NAV_ITEMS = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/payments', label: 'Payments', icon: ArrowLeftRight },
  { to: '/providers', label: 'Providers', icon: Server },
  { to: '/payouts', label: 'Payouts', icon: Send },
  { to: '/ledger', label: 'Ledger', icon: BookOpen },
  { to: '/reconciliation', label: 'Reconciliation', icon: FileCheck2 },
  { to: '/risk', label: 'Risk Center', icon: AlertTriangle },
  { to: '/health', label: 'System Health', icon: Activity },
  { to: '/audit', label: 'Audit Trail', icon: ShieldCheck },
  { to: '/admin', label: 'Administration', icon: Settings },
];

export function AppShell() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));

  const toggleTheme = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle('dark', next);
  };

  const currentNav =
    NAV_ITEMS.find((n) => (n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to))) ||
    NAV_ITEMS[0];

  return (
    <div className="min-h-screen flex bg-slate-50 dark:bg-navy-950 text-slate-900 dark:text-slate-100">
      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-30 w-64 bg-navy-900 text-slate-100 border-r border-navy-700 transform transition-transform lg:static lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="h-16 flex items-center gap-3 px-5 border-b border-navy-700">
          <div className="w-8 h-8 rounded-lg bg-emerald-500/15 border border-emerald-400/30 flex items-center justify-center">
            <Shield className="w-4 h-4 text-emerald-400" />
          </div>
          <div>
            <div className="font-semibold tracking-tight text-white">PayShield</div>
            <div className="text-[11px] uppercase tracking-wider text-slate-400">FinTech Ops Console</div>
          </div>
        </div>

        <nav className="p-3 space-y-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const active =
              item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to);
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={() => setSidebarOpen(false)}
                className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  active
                    ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                    : 'text-slate-300 hover:bg-navy-800 hover:text-white'
                }`}
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="absolute bottom-0 inset-x-0 p-3 border-t border-navy-700 bg-navy-950/60">
          <div className="px-2 py-1.5 rounded bg-amber-500/10 border border-amber-500/25 text-[11px] text-amber-300">
            SIMULATION ENVIRONMENT • SYNTHETIC DATA ONLY
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-16 bg-white dark:bg-navy-900 border-b border-slate-200 dark:border-navy-700 px-4 lg:px-6 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="lg:hidden p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-navy-800"
              aria-label="Toggle navigation"
            >
              <Menu className="w-5 h-5" />
            </button>
            <div className="text-sm text-slate-500 dark:text-slate-400">
              <span>PayShield</span>
              <span className="mx-2">/</span>
              <span className="font-medium text-slate-900 dark:text-white">{currentNav.label}</span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={toggleTheme}
              className="p-2 rounded-lg border border-slate-200 dark:border-navy-700 hover:bg-slate-100 dark:hover:bg-navy-800"
              aria-label="Toggle theme"
            >
              {dark ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-slate-600" />}
            </button>

            <div className="hidden sm:flex flex-col items-end text-xs">
              <span className="font-medium text-slate-900 dark:text-slate-100">{user?.fullName}</span>
              <span className="text-slate-500 dark:text-slate-400">{user?.roles.join(', ')}</span>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-slate-200 dark:border-navy-700 hover:bg-slate-100 dark:hover:bg-navy-800"
            >
              <LogOut className="w-3.5 h-3.5" />
              Sign out
            </button>
          </div>
        </header>

        <main className="flex-1 p-4 lg:p-6 max-w-[1440px] w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
