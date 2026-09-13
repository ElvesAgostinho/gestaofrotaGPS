/*
 * Service worker do IMBONDEIRO OS.
 *
 * Faz só o que uma app de campo precisa: abrir mesmo com rede fraca. A
 * «casca» (index.html, os ficheiros com hash em /assets/, os ícones) fica em
 * cache; os DADOS (/api/) nunca — vão sempre à rede, porque uma ordem ou uma
 * posição guardada no telemóvel de alguém que saiu da empresa é uma fuga.
 *
 * Sem rede: as páginas já visitadas abrem; a API responde 503 e a app diz
 * «sem ligação» em vez de mostrar dados velhos como se fossem de agora.
 */
const VERSAO = 'imbondeiro-shell-v2';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSAO).then((cache) => cache.addAll(['/', '/manifest.webmanifest', '/icons/icon-192.png']))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((chaves) => Promise.all(chaves.filter((c) => c !== VERSAO).map((c) => caches.delete(c))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // Dados: só rede. Nunca guardar.
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(request).catch(() => new Response(
      JSON.stringify({ message: 'Sem ligação à internet. Tente de novo quando tiver rede.' }),
      { status: 503, headers: { 'Content-Type': 'application/json' } })));
    return;
  }

  // Ficheiros com hash no nome: mudam de nome quando mudam; cache primeiro.
  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(
      caches.open(VERSAO).then(async (cache) => {
        const guardado = await cache.match(request);
        if (guardado) return guardado;
        const resposta = await fetch(request);
        if (resposta.ok) cache.put(request, resposta.clone());
        return resposta;
      }),
    );
    return;
  }

  // Navegação (qualquer caminho da SPA): rede primeiro, senão o index guardado.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).then((resposta) => {
        // A cópia faz-se já, antes de a página consumir o corpo da resposta.
        if (resposta.ok) {
          const copia = resposta.clone();
          caches.open(VERSAO).then((cache) => cache.put('/', copia)).catch(() => {});
        }
        return resposta;
      }).catch(() => caches.match('/')),
    );
  }
});
