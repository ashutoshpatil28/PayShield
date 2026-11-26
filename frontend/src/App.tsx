import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './lib/auth';
import { AppShell } from './components/AppShell';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { PaymentsPage } from './pages/PaymentsPage';
import { PaymentDetailPage } from './pages/PaymentDetailPage';
import { ProvidersPage } from './pages/ProvidersPage';
import { PayoutsPage } from './pages/PayoutsPage';
import { LedgerPage } from './pages/LedgerPage';
import { ReconciliationPage } from './pages/ReconciliationPage';
import { RiskPage } from './pages/RiskPage';
import { SystemHealthPage } from './pages/SystemHealthPage';
import { AuditTrailPage } from './pages/AuditTrailPage';
import { AdminPage } from './pages/AdminPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-navy-950 text-slate-300 text-sm">
        Verifying session...
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            >
              <Route index element={<OverviewPage />} />
              <Route path="payments" element={<PaymentsPage />} />
              <Route path="payments/:paymentId" element={<PaymentDetailPage />} />
              <Route path="providers" element={<ProvidersPage />} />
              <Route path="payouts" element={<PayoutsPage />} />
              <Route path="ledger" element={<LedgerPage />} />
              <Route path="reconciliation" element={<ReconciliationPage />} />
              <Route path="risk" element={<RiskPage />} />
              <Route path="health" element={<SystemHealthPage />} />
              <Route path="audit" element={<AuditTrailPage />} />
              <Route path="admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
export default App;
