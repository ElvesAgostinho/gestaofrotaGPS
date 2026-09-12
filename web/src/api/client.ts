// Cliente HTTP único. Injeta o token de acesso e renova-o automaticamente (uma vez) em 401.

const ACCESS_KEY = 'ac_access';
const REFRESH_KEY = 'ac_refresh';

export const tokens = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

export class ApiError extends Error {
  status: number;
  fields?: { field: string; message: string }[];
  constructor(message: string, status: number, fields?: { field: string; message: string }[]) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

let refreshing: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!tokens.refresh) return false;
  if (!refreshing) {
    refreshing = fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: tokens.refresh }),
    })
      .then(async (r) => {
        if (!r.ok) return false;
        const body = await r.json();
        tokens.set(body.accessToken, body.refreshToken);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

/**
 * Opções de um pedido.
 *
 * <p>`body` é o objecto em si, **não** uma string: o `api()` trata de o
 * converter. Aceitar `unknown` deixava passar `body: JSON.stringify({...})`,
 * que ia duplamente codificado e o servidor recusava com «O pedido não foi
 * entendido». O tipo abaixo faz o compilador apanhar isso.
 */
type Options = Omit<RequestInit, 'body'> & {
  body?: Record<string, unknown> | unknown[] | FormData;
  raw?: boolean;
};

export async function api<T = unknown>(path: string, opts: Options = {}): Promise<T> {
  const doFetch = () => {
    const headers = new Headers(opts.headers);
    if (tokens.access) headers.set('Authorization', `Bearer ${tokens.access}`);
    let body: BodyInit | undefined;
    if (opts.body instanceof FormData) {
      body = opts.body;
    } else if (opts.body !== undefined) {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(opts.body);
    }
    return fetch(`/api/v1${path}`, { ...opts, headers, body });
  };

  let res = await doFetch();
  if (res.status === 401 && (await tryRefresh())) {
    res = await doFetch();
  }
  if (res.status === 401) {
    tokens.clear();
    onUnauthorized();
    throw new ApiError('Sessão terminada.', 401);
  }

  if (res.status === 204) return undefined as T;
  const data = await readJson(res);
  // Resposta que não é JSON: o pedido não chegou ao backend (proxy, servidor em
  // baixo). Dizer isso é mais útil do que "não foi possível concluir".
  if (data === null && !res.ok) {
    throw serverUnreachable(res.status, res.headers.get('content-type'));
  }
  if (!res.ok) {
    throw new ApiError(
      (data && data.message) || 'Não foi possível concluir a operação.',
      res.status,
      data && data.errors,
    );
  }
  return data as T;
}

/**
 * Descarrega um ficheiro da API.
 *
 * <p>Existe porque o `api()` interpreta sempre a resposta como JSON, e um PDF
 * não é JSON. E tem de passar por aqui — e não por um `<a href>` — porque o
 * token vai no cabeçalho: uma ligação directa sairia sem autenticação.
 */
export async function apiBlob(path: string): Promise<Blob> {
  return (await fetchFile(path)).blob;
}

/**
 * Descarrega um ficheiro e devolve também o nome que o servidor lhe deu.
 *
 * <p>Quando corre mal, a resposta de erro é JSON e não um ficheiro: lê-se a
 * mensagem do servidor em vez de dizer só «não foi possível». Um 403 tem de
 * explicar que faltam permissões, não parecer uma avaria.
 */
async function fetchFile(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const headers = new Headers();
  if (tokens.access) headers.set('Authorization', `Bearer ${tokens.access}`);

  let res = await fetch(`/api/v1${path}`, { headers });
  if (res.status === 401 && (await tryRefresh())) {
    headers.set('Authorization', `Bearer ${tokens.access}`);
    res = await fetch(`/api/v1${path}`, { headers });
  }
  if (res.status === 401) {
    tokens.clear();
    onUnauthorized();
    throw new ApiError('Sessão terminada.', 401);
  }

  if (!res.ok) {
    if (res.status >= 500) throw serverUnreachable(res.status, res.headers.get('content-type'));
    const erro = await readJson(res);
    throw new ApiError(
      (erro && erro.message) || 'Não foi possível descarregar o ficheiro.',
      res.status,
    );
  }

  return { blob: await res.blob(), filename: filenameFrom(res) };
}

/** Lê o nome de ficheiro do cabeçalho `Content-Disposition`, se vier. */
function filenameFrom(res: Response): string | null {
  const cd = res.headers.get('Content-Disposition');
  if (!cd) return null;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(cd);
  if (utf8) return decodeURIComponent(utf8[1]);
  const simples = /filename="?([^";]+)"?/i.exec(cd);
  return simples ? simples[1] : null;
}

/**
 * Lê a resposta como JSON, sem rebentar quando ela não é JSON.
 *
 * <p>Quando o backend não está a responder, o servidor de desenvolvimento
 * devolve uma página de erro em HTML — e um `JSON.parse` sobre isso dava ao
 * utilizador «Unexpected token '<'», que não diz nada a ninguém. O mesmo
 * acontece em produção atrás de um proxy que devolva a sua própria página de
 * erro.
 */
async function readJson(res: Response): Promise<any> {
  let texto: string;
  try {
    texto = await res.text();
  } catch (e) {
    // Ligação cortada a meio da resposta (por exemplo um envio grande demais
    // que o servidor rejeitou sem esperar pelo resto).
    diagnostico(res, '<ligação cortada antes de a resposta chegar>');
    return null;
  }
  if (!texto) {
    if (!res.ok) diagnostico(res, '<corpo vazio>');
    return null;
  }
  try {
    return JSON.parse(texto);
  } catch {
    diagnostico(res, texto.slice(0, 300));
    return null;
  }
}

/**
 * Deixa no log da consola o que o servidor devolveu quando não foi JSON.
 *
 * <p>O utilizador recebe uma frase compreensível; o detalhe técnico fica aqui,
 * que é o que permite descobrir a causa em vez de adivinhar.
 */
function diagnostico(res: Response, corpo: string) {
  console.error(
    `[API] resposta não-JSON: ${res.status} ${res.url}
` +
      `      content-type: ${res.headers.get('content-type') ?? '(nenhum)'}
` +
      `      corpo: ${corpo}`,
  );
}

/**
 * Mensagem para quando a resposta não é sequer JSON.
 *
 * <p>Leva o código de estado entre parênteses. Não é um erro técnico atirado à
 * cara do utilizador — é uma referência, como o número que uma caixa
 * multibanco dá quando recusa. Sem ele, quem reporta o problema diz «deu erro»
 * e quem o tem de resolver fica a adivinhar; com ele, a primeira frase do
 * relato já diz o que aconteceu.
 */
function serverUnreachable(status: number, tipo?: string | null) {
  const ref = status > 0 ? ` (código ${status})` : '';

  // 400 com uma página HTML é quase sempre o servidor a recusar o cabeçalho
  // antes de o ler: cookies acumulados de outros projetos na mesma máquina.
  // Os cookies de `localhost` são partilhados por todas as portas, por isso
  // um site que correu ontem noutra porta continua a mandar os seus para aqui.
  // Dizer «resposta inesperada» deixava o utilizador sem nada para fazer.
  if (status === 400 && (tipo ?? '').includes('html')) {
    return new ApiError(
      'O seu navegador enviou dados a mais no pedido — normalmente cookies '
        + 'acumulados de outros sites em localhost. Limpe os cookies de '
        + 'localhost, ou abra numa janela privada.',
      status,
    );
  }

  return new ApiError(
    status >= 500 || status === 0
      ? `Não foi possível contactar o servidor. Verifique se ele está a funcionar e tente novamente.${ref}`
      : `O servidor devolveu uma resposta inesperada. Tente novamente daqui a instantes.${ref}`,
    status,
  );
}

export async function login(identifier: string, password: string) {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identifier, password }),
  });
  const body = await readJson(res);
  if (body === null) throw serverUnreachable(res.status, res.headers.get('content-type'));
  if (!res.ok) throw new ApiError(body.message || `Falha no início de sessão (código ${res.status})`, res.status);
  tokens.set(body.accessToken, body.refreshToken);
  return body.user;
}

export async function registerAccount(input: {
  name: string;
  email: string;
  password: string;
  organizationName?: string;
}) {
  const res = await fetch('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...input, acceptTerms: true }),
  });
  const body = await readJson(res);
  if (body === null) throw serverUnreachable(res.status, res.headers.get('content-type'));
  if (!res.ok) throw new ApiError(body.message || 'Não foi possível criar a conta', res.status);
  tokens.set(body.accessToken, body.refreshToken);
  return body.user;
}

/**
 * Limite de tamanho de ficheiro, igual ao do backend (`max-file-size`).
 *
 * <p>Verificar aqui não substitui a validação do servidor — substitui a espera:
 * sem isto o utilizador envia o ficheiro inteiro por uma ligação lenta só para
 * ouvir que não servia. Em Angola isso custa dados e paciência.
 */
export const MAX_UPLOAD_BYTES = 15 * 1024 * 1024;

/** Devolve a mensagem de recusa, ou `null` se o ficheiro serve. */
export function checkUploadSize(file: File): string | null {
  if (file.size === 0) return 'O ficheiro está vazio.';
  if (file.size > MAX_UPLOAD_BYTES) {
    // Arredondar para cima: a 15 MB + 1 byte, `toFixed` dava «tem 15.0 MB e o
    // limite é 15 MB», que se lê como uma contradição.
    const mb = (Math.ceil((file.size / 1024 / 1024) * 10) / 10).toFixed(1);
    return `O ficheiro tem ${mb} MB e o limite é 15 MB. Reduza-o e tente de novo.`;
  }
  return null;
}

/**
 * Descarrega um ficheiro da API para o disco do utilizador.
 *
 * <p>Tem de passar por aqui e não por um `<a href>` directo: o token vai no
 * cabeçalho, por isso uma ligação normal sairia sem autenticação e o servidor
 * respondia 401.
 */
/**
 * Abre um ficheiro (PDF) num separador novo, com a sessão.
 *
 * <p>Um {@code <a href>} para a API abre sem token e devolve 401 — foi o erro
 * dos relatórios, e estava também no plano em PDF. O ficheiro vem por fetch
 * autenticado e abre-se a partir de um URL local.
 */
export async function openFile(path: string) {
  // A janela abre-se antes do fetch: um window.open depois de um await é
  // bloqueado pelos navegadores como popup não pedido.
  const janela = window.open('', '_blank');
  try {
    const ficheiro = await fetchFile(path);
    const url = URL.createObjectURL(ficheiro.blob);
    if (janela) janela.location.href = url;
    else window.open(url, '_blank');
  } catch (e) {
    janela?.close();
    throw e;
  }
}

export async function downloadFile(path: string, filename: string) {
  const ficheiro = await fetchFile(path);
  // O servidor sabe melhor como se chama o relatório («manutencao-por-ativo»)
  // do que a rota que o pediu.
  const nome = ficheiro.filename ?? filename;
  const url = URL.createObjectURL(ficheiro.blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
