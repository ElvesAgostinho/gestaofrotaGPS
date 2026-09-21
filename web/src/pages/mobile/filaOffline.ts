/**
 * A fila de envio: o que se regista sem rede não se perde.
 *
 * <p>Metade das estradas de Angola não tem cobertura, e é lá que a inspeção é
 * feita, o depósito é atestado e a avaria acontece. Se a aplicação exigisse
 * rede no momento do registo, o motorista faria o que faz hoje: nada, e depois
 * conta de cabeça ao chegar.
 *
 * <p>Por isso tudo o que ele envia passa por aqui. Se a rede responder, segue
 * logo. Se não, fica guardado em IndexedDB — <b>com as fotografias e tudo</b>,
 * porque um <i>Blob</i> guarda-se tal como está — e sobe sozinho quando a
 * ligação voltar. O número do que falta enviar está sempre à vista: uma fila
 * escondida seria pior do que não a ter.
 */

const BD = 'imbondeiro-fila';
const LOJA = 'pedidos';

export interface PedidoEmFila {
  id?: number;
  /** Caminho da API, já sem o prefixo (ex.: «/mobile/occurrences»). */
  url: string;
  method: string;
  /** Corpo JSON, quando não há ficheiros. */
  json?: unknown;
  /** Campos de formulário, para os envios com fotografias. */
  form?: { name: string; value: string | Blob; filename?: string }[];
  /** O que se mostra na lista: «Inspeção diária · CAM-001». */
  descricao: string;
  criadoEm: number;
}

type Ouvinte = (porEnviar: number) => void;
const ouvintes = new Set<Ouvinte>();

function abrir(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(BD, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(LOJA)) {
        db.createObjectStore(LOJA, { keyPath: 'id', autoIncrement: true });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function comLoja<T>(modo: IDBTransactionMode, fn: (loja: IDBObjectStore) => IDBRequest): Promise<T> {
  const db = await abrir();
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(LOJA, modo);
    const req = fn(tx.objectStore(LOJA));
    req.onsuccess = () => resolve(req.result as T);
    req.onerror = () => reject(req.error);
  });
}

export async function listar(): Promise<PedidoEmFila[]> {
  try {
    return (await comLoja<PedidoEmFila[]>('readonly', (l) => l.getAll())) ?? [];
  } catch {
    return [];
  }
}

async function contar(): Promise<number> {
  return (await listar()).length;
}

async function avisar() {
  const n = await contar();
  ouvintes.forEach((o) => o(n));
}

export function subscreverFila(o: Ouvinte): () => void {
  ouvintes.add(o);
  void contar().then(o);
  return () => ouvintes.delete(o);
}

export async function guardar(p: Omit<PedidoEmFila, 'id' | 'criadoEm'>): Promise<void> {
  await comLoja('readwrite', (l) => l.add({ ...p, criadoEm: Date.now() }));
  await avisar();
}

async function apagar(id: number): Promise<void> {
  await comLoja('readwrite', (l) => l.delete(id));
  await avisar();
}

/** Monta o corpo do pedido tal como ele foi guardado. */
function corpoDe(p: PedidoEmFila): BodyInit | undefined {
  if (p.form) {
    const fd = new FormData();
    for (const campo of p.form) {
      if (typeof campo.value === 'string') {
        fd.append(campo.name, campo.value);
      } else {
        fd.append(campo.name, campo.value, campo.filename ?? 'foto.jpg');
      }
    }
    return fd;
  }
  if (p.json !== undefined) {
    return JSON.stringify(p.json);
  }
  return undefined;
}

/**
 * Tenta enviar tudo o que está em fila, pela ordem em que foi registado.
 *
 * <p>Pára ao primeiro que falhe por falta de rede — não vale a pena insistir
 * com os seguintes. Um pedido recusado pelo servidor (400, 409) sai da fila:
 * tentá-lo para sempre encheria a fila com o que nunca vai ser aceite.
 */
export async function esvaziar(
  enviar: (p: PedidoEmFila, corpo: BodyInit | undefined) => Promise<void>,
): Promise<{ enviados: number; falhados: number }> {
  const fila = (await listar()).sort((a, b) => a.criadoEm - b.criadoEm);
  let enviados = 0;
  let falhados = 0;
  for (const p of fila) {
    try {
      await enviar(p, corpoDe(p));
      if (p.id != null) await apagar(p.id);
      enviados++;
    } catch (e) {
      const semRede = e instanceof TypeError || (e as { semRede?: boolean })?.semRede;
      if (semRede) {
        falhados++;
        break;
      }
      // O servidor recusou: fica registado no ecrã e sai da fila.
      if (p.id != null) await apagar(p.id);
      falhados++;
    }
  }
  await avisar();
  return { enviados, falhados };
}
