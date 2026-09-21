import React from 'react';
import ReactDOM from 'react-dom/client';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/dates/styles.css';
import './erp.css';
import { theme } from './theme';
import { App } from './App';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 15_000 },
  },
});

// A pele ERP vai no elemento raiz do documento e não num <div> da aplicação:
// os modais e as notificações do Mantine são montados fora da árvore da app e
// de outra forma ficariam com o aspeto antigo.
document.documentElement.classList.add('erp');

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="auto">
      <Notifications position="top-right" />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  </React.StrictMode>,
);

// A app instalável: só em produção, para o service worker não esconder
// alterações durante o desenvolvimento.
// O browser oferece a instalação uma única vez e num momento que não é o
// nosso: guarda-se o convite para o mostrarmos quando fizer sentido — no
// perfil, com um botão que diz o que faz.
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  (window as unknown as { __instalar?: unknown }).__instalar = e;
});

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* sem service worker a app continua a funcionar, só não instala */
    });
  });
}
