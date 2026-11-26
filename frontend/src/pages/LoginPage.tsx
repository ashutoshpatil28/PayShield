import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, Lock, AlertCircle } from 'lucide-react';
import { useAuth } from '../lib/auth';
import { ApiError } from '../lib/api';

const DEMO_ACCOUNTS = [
  { label: 'Platform Admin', email: 'admin@payshield.local' },
  { label: 'Acme Merchant Admin', email: 'acme.merchant@payshield.local' },
  { label: 'Operations Analyst', email: 'ops@payshield.local' },
  { label: 'Risk Analyst', email: 'risk@payshield.local' },
  { label: 'Payout Maker', email: 'maker@payshield.local' },
  { label: 'Payout Checker', email: 'checker@payshield.local' },
  { label: 'Financial Auditor', email: 'auditor@payshield.local' },
];

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@payshield.local');
  const [password, setPassword] = useState('PayShield@Demo2026!');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Unable to connect to PayShield backend.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-navy-950 px-4 py-10">
      <div className="w-full max-w-md bg-navy-900 border border-navy-700 rounded-xl shadow-2xl p-8 text-slate-100">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 rounded-lg bg-emerald-500/15 border border-emerald-400/30 flex items-center justify-center">
            <Shield className="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-white">PayShield Operations</h1>
            <p className="text-xs text-slate-400">Enterprise Payment Infrastructure Simulation</p>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-start gap-2">
            <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-xs font-medium text-slate-300 mb-1.5">
              Work Email
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-navy-950 border border-navy-700 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-xs font-medium text-slate-300 mb-1.5">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-navy-950 border border-navy-700 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-medium text-sm flex items-center justify-center gap-2 transition-colors"
          >
            <Lock className="w-4 h-4" />
            {submitting ? 'Authenticating...' : 'Sign in to Console'}
          </button>
        </form>

        <div className="mt-6 pt-5 border-t border-navy-700">
          <div className="text-[11px] uppercase tracking-wider text-slate-400 mb-2">
            Seeded Local Demo Accounts (Password: PayShield@Demo2026!)
          </div>
          <div className="flex flex-wrap gap-1.5">
            {DEMO_ACCOUNTS.map((acc) => (
              <button
                key={acc.email}
                type="button"
                onClick={() => {
                  setEmail(acc.email);
                  setPassword('PayShield@Demo2026!');
                }}
                className="px-2 py-1 rounded text-[11px] bg-navy-800 hover:bg-navy-700 border border-navy-700 text-slate-300"
              >
                {acc.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
