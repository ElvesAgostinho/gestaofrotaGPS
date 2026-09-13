import { Center, Loader } from '@mantine/core';
import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { useConfigPublica } from './lib/marca';
import { Shell } from './layout/Shell';
import { AlertsPage } from './pages/AlertsPage';
import { AssetDetailPage } from './pages/AssetDetailPage';
import { AparelhosGpsPage } from './pages/AparelhosGpsPage';
import { GuiasTransportePage } from './pages/GuiasTransportePage';
import { TiposAtivoPage } from './pages/TiposAtivoPage';
import { AssetsPage } from './pages/AssetsPage';
import { AuditPage } from './pages/AuditPage';
import { CommandsPage } from './pages/CommandsPage';
import { DashboardPage } from './pages/DashboardPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DriversPage } from './pages/DriversPage';
import { DrivingPage } from './pages/DrivingPage';
import { FuelPage } from './pages/FuelPage';
import { RoutesPage } from './pages/RoutesPage';
import { LocationsPage } from './pages/LocationsPage';
import { LandingPage } from './pages/publico/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { PartsPage } from './pages/PartsPage';
import { PlataformaPage } from './pages/PlataformaPage';
import { PlanosPage } from './pages/PlanosPage';
import { OrcamentosPage } from './pages/OrcamentosPage';
import { EmpresaBloqueadaPage } from './pages/EmpresaBloqueadaPage';
import { PredictivePage } from './pages/PredictivePage';
import { ReportsPage } from './pages/ReportsPage';
import { SettingsPage } from './pages/SettingsPage';
import { TeamPage } from './pages/TeamPage';
import { WorkOrderDetailPage } from './pages/WorkOrderDetailPage';
import { WorkOrdersPage } from './pages/WorkOrdersPage';

// O MapLibre pesa mais do que todo o resto da aplicação junta e só serve uma
// página. Carregá-lo à parte tira ~250 kB do primeiro arranque — que numa
// ligação móvel angolana é a diferença entre abrir depressa e parecer avariado.
const MapPage = lazy(() => import('./pages/MapPage').then((m) => ({ default: m.MapPage })));

export function App() {
  return (
    <AuthProvider>
      <Router />
    </AuthProvider>
  );
}

function Router() {
  const { user, org, loading } = useAuth();
  const cfg = useConfigPublica();
  const marcaBranca = !!cfg?.brand;

  // Enquanto não se sabe se há sessão, não se decide nada: mostrar o ecrã de
  // entrada e logo a seguir o painel faria a página piscar a cada recarga.
  if (loading) {
    return (
      <Center h="100vh">
        <Loader />
      </Center>
    );
  }

  if (!user) {
    return (
      <Routes>
        {/* Quem chega sem sessão vê primeiro o que o sistema é. Mandar um
            visitante directo para o formulário de entrada é pedir a palavra-passe
            a quem ainda nem sabe o que está a comprar. */}
        {/* Com marca branca, a raiz é o ecrã de entrada do cliente — a página
            pública é a do IMBONDEIRO OS, não a dele. */}
        <Route path="/" element={marcaBranca ? <LoginPage /> : <LandingPage />} />
        <Route path="/entrar" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );
  }

  // Empresa suspensa pela plataforma ou com a licença vencida: o servidor
  // recusa tudo menos o essencial, por isso o ecrã diz porquê em vez de
  // mostrar um painel cheio de erros.
  if (org?.blockedReason && !user.admin) {
    return <EmpresaBloqueadaPage motivo={org.blockedReason} />;
  }

  // O administrador da plataforma sem empresa própria só tem a Plataforma.
  if (user.admin && !org) {
    return (
      <Routes>
        <Route element={<Shell />}>
          <Route path="/plataforma" element={<PlataformaPage />} />
          <Route path="*" element={<Navigate to="/plataforma" replace />} />
        </Route>
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/entrar" element={<Navigate to="/" replace />} />
      <Route element={<Shell />}>
        <Route path="/" element={<DashboardPage />} />
        {user.admin && <Route path="/plataforma" element={<PlataformaPage />} />}
        <Route path="/ativos" element={<AssetsPage />} />
        <Route path="/tipos-equipamento" element={<TiposAtivoPage />} />
        <Route path="/ativos/:id" element={<AssetDetailPage />} />
        <Route path="/aparelhos-gps" element={<AparelhosGpsPage />} />
        <Route
          path="/mapa"
          element={
            <Suspense
              fallback={
                <Center h="60vh">
                  <Loader />
                </Center>
              }
            >
              <MapPage />
            </Suspense>
          }
        />
        <Route path="/guias" element={<GuiasTransportePage />} />
        <Route path="/ordens" element={<WorkOrdersPage />} />
        <Route path="/ordens/:id" element={<WorkOrderDetailPage />} />
        <Route path="/planos" element={<PlanosPage />} />
        <Route path="/preditiva" element={<PredictivePage />} />
        <Route path="/pecas" element={<PartsPage />} />
        <Route path="/documentos" element={<DocumentsPage />} />
        <Route path="/alertas" element={<AlertsPage />} />
        <Route path="/comandos" element={<CommandsPage />} />
        <Route path="/relatorios" element={<ReportsPage />} />
        <Route path="/orcamentos" element={<OrcamentosPage />} />
        <Route path="/equipa" element={<TeamPage />} />
        <Route path="/motoristas" element={<DriversPage />} />
        <Route path="/conducao" element={<DrivingPage />} />
        <Route path="/combustivel" element={<FuelPage />} />
        <Route path="/rotas" element={<RoutesPage />} />
        <Route path="/filiais" element={<LocationsPage />} />
        <Route path="/auditoria" element={<AuditPage />} />
        <Route path="/definicoes" element={<SettingsPage />} />
        <Route path="/notificacoes" element={<NotificationsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
