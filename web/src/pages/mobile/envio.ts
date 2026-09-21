/**
 * Enviar agora, ou guardar para quando houver rede.
 *
 * <p>É a única porta por onde a aplicação do motorista escreve no sistema.
 * Tenta a rede; se ela não estiver lá, mete na fila e diz que ficou guardado.
 * Do ponto de vista de quem está a usar, o registo foi feito — e foi mesmo: só
 * chega ao servidor mais tarde.
 */
import { api } from '../../api/client';
import { esvaziar, guardar, subscreverFila, type PedidoEmFila } from './filaOffline';

export type Resultado<T> = { enviado: true; dados: T } | { enviado: false; emFila: true };

/** Falta de rede — e não uma recusa do servidor. */
function semRede(e: unknown): boolean {
  return e instanceof TypeError || !navigator.onLine;
}

/** Envia um pedido em JSON; sem rede, fica em fila. */
export async function enviarJson<T>(
  url: string,
  json: unknown,
  descricao: string,
): Promise<Resultado<T>> {
  try {
    const dados = await api<T>(url, { method: 'POST', body: json as Record<string, unknown> });
    return { enviado: true, dados };
  } catch (e) {
    if (!semRede(e)) throw e;
    await guardar({ url, method: 'POST', json, descricao });
    return { enviado: false, emFila: true };
  }
}

/** O mesmo para envios com fotografias. */
export async function enviarFormulario<T>(
  url: string,
  campos: { name: string; value: string | Blob; filename?: string }[],
  descricao: string,
): Promise<Resultado<T>> {
  const fd = new FormData();
  for (const c of campos) {
    if (typeof c.value === 'string') fd.append(c.name, c.value);
    else fd.append(c.name, c.value, c.filename ?? 'foto.jpg');
  }
  try {
    const dados = await api<T>(url, { method: 'POST', body: fd });
    return { enviado: true, dados };
  } catch (e) {
    if (!semRede(e)) throw e;
    await guardar({ url, method: 'POST', form: campos, descricao });
    return { enviado: false, emFila: true };
  }
}

/** Tenta subir o que está guardado. Chamado ao arrancar e quando a rede volta. */
export async function sincronizar(): Promise<{ enviados: number; falhados: number }> {
  return esvaziar(async (p: PedidoEmFila, corpo) => {
    // O corpo já vem montado da fila: FormData ou JSON.
    if (corpo instanceof FormData) {
      await api(p.url, { method: p.method, body: corpo });
    } else {
      await api(p.url, { method: p.method, body: p.json as Record<string, unknown> });
    }
  });
}

/** Liga a sincronização automática: ao abrir a aplicação e quando a rede volta. */
export function ligarSincronizacaoAutomatica() {
  const tentar = () => {
    if (navigator.onLine) void sincronizar();
  };
  window.addEventListener('online', tentar);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') tentar();
  });
  tentar();
}

export { subscreverFila };
