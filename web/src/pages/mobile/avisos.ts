/**
 * Pedir ao telemóvel autorização para avisar.
 *
 * <p>Não se pede à entrada. Um pedido de notificações que aparece antes de a
 * pessoa perceber para que serve a aplicação é quase sempre recusado — e, uma
 * vez recusado, o browser não volta a perguntar. Pede-se quando há motivo: no
 * perfil, com um botão que diz o que vai receber.
 */
import { api } from '../../api/client';

const GUARDADO = 'imbondeiro.avisos.subscrito';

/** Converte a chave do servidor no formato que o browser exige. */
function chaveParaBytes(base64: string): BufferSource {
  const completo = (base64 + '='.repeat((4 - (base64.length % 4)) % 4))
    .replace(/-/g, '+')
    .replace(/_/g, '/');
  const bruto = atob(completo);
  const bytes = new Uint8Array(new ArrayBuffer(bruto.length));
  for (let i = 0; i < bruto.length; i++) bytes[i] = bruto.charCodeAt(i);
  return bytes;
}

function base64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return '';
  const bytes = new Uint8Array(buffer);
  let s = '';
  bytes.forEach((b) => {
    s += String.fromCharCode(b);
  });
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function avisosSuportados(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

export function avisosLigados(): boolean {
  try {
    return localStorage.getItem(GUARDADO) === '1' && Notification.permission === 'granted';
  } catch {
    return false;
  }
}

/** Pede autorização e regista este aparelho no servidor. */
export async function ligarAvisos(): Promise<{ ok: boolean; motivo?: string }> {
  if (!avisosSuportados()) {
    return { ok: false, motivo: 'Este telemóvel (ou este browser) não suporta avisos.' };
  }
  const permissao = await Notification.requestPermission();
  if (permissao !== 'granted') {
    return {
      ok: false,
      motivo:
        'Autorização recusada. Pode voltar a permitir nas definições do browser, em «Notificações».',
    };
  }
  const registo = await navigator.serviceWorker.ready;
  const { publicKey } = await api<{ publicKey: string }>('/push/public-key');
  const subscricao =
    (await registo.pushManager.getSubscription()) ??
    (await registo.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: chaveParaBytes(publicKey),
    }));

  await api('/push/subscriptions', {
    method: 'POST',
    body: {
      endpoint: subscricao.endpoint,
      p256dh: base64Url(subscricao.getKey('p256dh')),
      auth: base64Url(subscricao.getKey('auth')),
    },
  });
  try {
    localStorage.setItem(GUARDADO, '1');
  } catch {
    /* sem armazenamento, volta a perguntar da próxima */
  }
  return { ok: true };
}

/** Desliga os avisos neste aparelho — usado ao terminar sessão. */
export async function desligarAvisos(): Promise<void> {
  try {
    localStorage.removeItem(GUARDADO);
    if (!avisosSuportados()) return;
    const registo = await navigator.serviceWorker.ready;
    const subscricao = await registo.pushManager.getSubscription();
    if (!subscricao) return;
    await api('/push/subscriptions', {
      method: 'DELETE',
      body: { endpoint: subscricao.endpoint },
    }).catch(() => undefined);
    await subscricao.unsubscribe();
  } catch {
    /* sair da sessão nunca falha por causa dos avisos */
  }
}
