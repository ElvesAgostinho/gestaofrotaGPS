/**
 * Saber que há uma versão nova — sem ninguém ter de limpar nada.
 *
 * <p>Isto existe por causa de um caso real: uma funcionalidade foi publicada,
 * estava no servidor, e quem a tinha pedido não a via no ecrã. O separador
 * estava aberto desde antes da publicação e o service worker nunca voltou a
 * perguntar ao servidor se havia coisa nova. Do lado de quem usa, isso é
 * indistinguível de «não fizeram o trabalho».
 *
 * <p>Dizer «faça Ctrl+Shift+R» não é resposta para quem vende o sistema a
 * empresas. Portanto o sistema pergunta sozinho: ao abrir, de meia em meia
 * hora, e sempre que o separador volta a ficar à frente — que é o momento em
 * que alguém regressa ao trabalho. Quando há versão nova, aparece uma barra a
 * dizê-lo, com um botão. Nunca se recarrega a página por iniciativa própria:
 * a pessoa pode estar a meio de um formulário, e perder o que escreveu é pior
 * do que ficar uma hora com a versão antiga.
 */

const MEIA_HORA = 30 * 60 * 1000;

let registo: ServiceWorkerRegistration | null = null;
let haVersaoNova = false;
const ouvintes = new Set<(ha: boolean) => void>();

/** Avisa quem está a mostrar a barra. */
function anunciar() {
  ouvintes.forEach((f) => f(haVersaoNova));
}

export function subscreverVersaoNova(f: (ha: boolean) => void): () => void {
  ouvintes.add(f);
  f(haVersaoNova);
  return () => ouvintes.delete(f);
}

/**
 * Pergunta ao servidor se há versão nova.
 *
 * @returns {@code true} se ficou alguma coisa a instalar, {@code false} se já
 *     se está na última versão, e {@code null} se não há como saber (sem
 *     service worker, em desenvolvimento, ou sem rede).
 */
export async function verificarActualizacao(): Promise<boolean | null> {
  if (!registo) {
    return null;
  }
  try {
    await registo.update();
  } catch {
    return null;
  }
  // O sw.js chama skipWaiting(), portanto o novo trabalhador passa por
  // «installing» e assume logo o comando; não fica à espera em «waiting».
  const aInstalar = registo.installing ?? registo.waiting;
  if (aInstalar) {
    haVersaoNova = true;
    anunciar();
    return true;
  }
  return false;
}

/** Instala a versão nova: é só recarregar, o trabalhador novo já tomou conta. */
export function aplicarActualizacao() {
  // `?v=` obriga o browser a ir buscar o index de novo em vez de o reaproveitar
  // de uma cópia sua — o caso em que a barra aparecia e o botão não fazia nada.
  const url = new URL(window.location.href);
  url.searchParams.set('v', String(Date.now()));
  window.location.replace(url.toString());
}

/** Liga a verificação periódica. Chamada uma vez, no arranque. */
export function ligarActualizacoes(r: ServiceWorkerRegistration) {
  registo = r;

  // Um trabalhador novo a assumir o comando significa que o que está em
  // memória nesta página já não é a versão que o servidor serve. Na primeira
  // instalação não há nada a anunciar: não havia versão anterior.
  const jaControlado = !!navigator.serviceWorker.controller;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (jaControlado) {
      haVersaoNova = true;
      anunciar();
    }
  });

  void verificarActualizacao();
  setInterval(() => void verificarActualizacao(), MEIA_HORA);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      void verificarActualizacao();
    }
  });
}
