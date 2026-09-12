import { ActionIcon, Checkbox, Group, Menu, Select, Text, Tooltip } from '@mantine/core';
import {
  IconChevronLeft,
  IconChevronRight,
  IconColumns3,
  IconSortAscending,
  IconSortDescending,
} from '@tabler/icons-react';
import {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react';

/**
 * A grelha de um ERP.
 *
 * <p>O que separa um ERP de um SaaS não é a cor: é a densidade e o
 * comportamento da grelha. Quem passa oito horas por dia num Primavera ordena
 * clicando no cabeçalho, esconde as colunas que não usa, desce com as setas,
 * abre com Enter ou duplo clique, e vê no rodapé quantos registos há. Uma lista
 * que só se lê com o rato e não diz quantas linhas tem é um relatório, não uma
 * ferramenta de trabalho.
 *
 * <p>Ordenação, colunas escondidas e tamanho de página ficam guardados por
 * grelha no navegador: a pessoa arruma a grelha uma vez e encontra-a arrumada
 * amanhã.
 */

export type Primitivo = string | number | boolean | null | undefined;

export interface Coluna<T> {
  id: string;
  titulo: string;
  /** Largura fixa em px; sem ela a coluna reparte o espaço que sobra. */
  largura?: number;
  alinhar?: 'left' | 'right' | 'center';
  /**
   * Valor cru, usado para ordenar. Sem ele a coluna não ordena — é o caso das
   * colunas de botões.
   */
  valor?: (linha: T) => Primitivo;
  /** O que se desenha. Por omissão, o valor. */
  render?: (linha: T) => ReactNode;
  /** Escondida até a pessoa a pedir. */
  escondida?: boolean;
  /** Não se pode esconder: a etiqueta, o número — o que identifica a linha. */
  fixa?: boolean;
  /** Corta em vez de quebrar linha. */
  semQuebra?: boolean;
}

export interface Ordenacao {
  coluna: string;
  direcao: 'asc' | 'desc';
}

/**
 * Paginação e ordenação feitas pelo servidor.
 *
 * <p>Quando existe, a grelha não ordena nem pagina por si: pede. Ordenar só a
 * página carregada e fingir que se ordenou o todo é o tipo de mentira que uma
 * grelha de ERP não pode contar.
 */
export interface Servidor {
  /** Página atual, a contar de 1. */
  pagina: number;
  totalPaginas: number;
  total: number;
  aoMudarPagina: (pagina: number) => void;
  aoMudarTamanho?: (tamanho: number) => void;
  ordenacao?: Ordenacao | null;
  /** Ausente: os cabeçalhos não ordenam. Presente: pedem ao servidor. */
  aoOrdenar?: (o: Ordenacao | null) => void;
}

interface Props<T> {
  /** Identifica a grelha para guardar a arrumação. */
  id: string;
  colunas: Coluna<T>[];
  linhas: T[];
  chave: (linha: T) => string;
  /** Enter ou duplo clique. */
  aoAbrir?: (linha: T) => void;
  carregando?: boolean;
  vazio?: ReactNode;
  porPagina?: number;
  servidor?: Servidor;
  /**
   * Agrupa linhas consecutivas com o mesmo rótulo, com uma linha de grupo à
   * frente. A ordenação respeita o grupo: ordena dentro dele.
   */
  grupo?: (linha: T) => string;
  /**
   * Ordem dos grupos. Sem isto ordenam-se pelo rótulo, o que põe «Geradores»
   * à frente de «Máquinas pesadas» quando a empresa quer o contrário.
   */
  ordemGrupo?: (linha: T) => number;
  /** Altura da zona que rola. A grelha rola por dentro, como num ERP. */
  altura?: string;
  /** Texto extra no rodapé, à esquerda da contagem. */
  rodape?: ReactNode;
  /** Largura mínima da tabela antes de rolar na horizontal. */
  larguraMinima?: number;
}

interface Arrumacao {
  escondidas: string[];
  ordenacao: Ordenacao | null;
  porPagina: number;
}

const TAMANHOS = ['25', '50', '100', '200'];

function lerArrumacao(id: string): Partial<Arrumacao> {
  try {
    const v = localStorage.getItem('grelha:' + id);
    return v ? (JSON.parse(v) as Partial<Arrumacao>) : {};
  } catch {
    return {};
  }
}

function guardarArrumacao(id: string, a: Arrumacao) {
  try {
    localStorage.setItem('grelha:' + id, JSON.stringify(a));
  } catch {
    // Sem armazenamento (janela privada): a grelha continua a funcionar,
    // só não se lembra de amanhã.
  }
}

function comparar(a: Primitivo, b: Primitivo): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1; // vazios no fim, em qualquer direção
  if (b == null) return -1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b);
  return String(a).localeCompare(String(b), 'pt', { numeric: true, sensitivity: 'base' });
}

export function Grelha<T>({
  id,
  colunas,
  linhas,
  chave,
  aoAbrir,
  carregando,
  vazio,
  porPagina: porPaginaInicial = 25,
  servidor,
  grupo,
  ordemGrupo,
  altura = 'calc(100vh - 262px)',
  rodape,
  larguraMinima,
}: Props<T>) {
  const guardado = useMemo(() => lerArrumacao(id), [id]);

  const [escondidas, setEscondidas] = useState<Set<string>>(
    () => new Set(guardado.escondidas ?? colunas.filter((c) => c.escondida).map((c) => c.id)),
  );
  const [ordenacaoLocal, setOrdenacaoLocal] = useState<Ordenacao | null>(
    guardado.ordenacao ?? null,
  );
  const [porPagina, setPorPagina] = useState<number>(guardado.porPagina ?? porPaginaInicial);
  const [pagina, setPagina] = useState(1);
  const [selecionada, setSelecionada] = useState<string | null>(null);

  const corpo = useRef<HTMLDivElement>(null);

  const ordenacao = servidor ? (servidor.ordenacao ?? null) : ordenacaoLocal;

  useEffect(() => {
    guardarArrumacao(id, { escondidas: [...escondidas], ordenacao: ordenacaoLocal, porPagina });
  }, [id, escondidas, ordenacaoLocal, porPagina]);

  // Mudou a lista (procura, filtro): volta à primeira página, ou a pessoa fica
  // a olhar para uma página 4 que já não existe.
  useEffect(() => {
    setPagina(1);
  }, [linhas.length]);

  const visiveis = colunas.filter((c) => !escondidas.has(c.id));

  // ---- ordenar e paginar (só quando não é o servidor a fazê-lo) ------------
  const ordenadas = useMemo(() => {
    if (servidor) return linhas;
    const col = ordenacao ? colunas.find((c) => c.id === ordenacao.coluna) : undefined;
    if (!col?.valor && !grupo) return linhas;
    const sinal = ordenacao?.direcao === 'desc' ? -1 : 1;
    // Ordenação estável e que respeita o grupo: os grupos pela sua ordem, as
    // linhas pela coluna dentro de cada grupo.
    return [...linhas]
      .map((l, i) => ({ l, i }))
      .sort((x, y) => {
        if (grupo) {
          const g = ordemGrupo
            ? ordemGrupo(x.l) - ordemGrupo(y.l)
            : comparar(grupo(x.l), grupo(y.l));
          if (g !== 0) return g;
        }
        const c = col?.valor ? comparar(col.valor(x.l), col.valor(y.l)) * sinal : 0;
        return c !== 0 ? c : x.i - y.i;
      })
      .map((x) => x.l);
  }, [linhas, ordenacao, colunas, servidor, grupo, ordemGrupo]);

  const totalPaginas = servidor
    ? Math.max(1, servidor.totalPaginas)
    : Math.max(1, Math.ceil(ordenadas.length / porPagina));
  const paginaAtual = servidor ? servidor.pagina : Math.min(pagina, totalPaginas);
  const pagina_ = servidor
    ? ordenadas
    : ordenadas.slice((paginaAtual - 1) * porPagina, paginaAtual * porPagina);
  const total = servidor ? servidor.total : ordenadas.length;
  const primeiro = total === 0 ? 0 : (paginaAtual - 1) * porPagina + 1;
  const ultimo = servidor
    ? Math.min(total, primeiro + pagina_.length - 1)
    : Math.min(total, paginaAtual * porPagina);

  function irPara(p: number) {
    const alvo = Math.min(Math.max(1, p), totalPaginas);
    if (servidor) servidor.aoMudarPagina(alvo);
    else setPagina(alvo);
  }

  function mudarTamanho(t: number) {
    setPorPagina(t);
    if (servidor) servidor.aoMudarTamanho?.(t);
    else setPagina(1);
  }

  function ordenarPor(col: Coluna<T>) {
    if (!col.valor) return;
    if (servidor && !servidor.aoOrdenar) return;
    const proxima: Ordenacao | null =
      ordenacao?.coluna !== col.id
        ? { coluna: col.id, direcao: 'asc' }
        : ordenacao.direcao === 'asc'
          ? { coluna: col.id, direcao: 'desc' }
          : null;
    if (servidor) servidor.aoOrdenar!(proxima);
    else setOrdenacaoLocal(proxima);
  }

  const podeOrdenar = (col: Coluna<T>) => !!col.valor && (!servidor || !!servidor.aoOrdenar);

  // ---- teclado ---------------------------------------------------------------
  const chaves = pagina_.map(chave);

  const mover = useCallback(
    (delta: number) => {
      if (chaves.length === 0) return;
      const i = selecionada ? chaves.indexOf(selecionada) : -1;
      const alvo = Math.min(Math.max(0, i + delta), chaves.length - 1);
      setSelecionada(chaves[alvo]);
      corpo.current
        ?.querySelector<HTMLElement>(`tr[data-chave="${CSS.escape(chaves[alvo])}"]`)
        ?.scrollIntoView({ block: 'nearest' });
    },
    [chaves, selecionada],
  );

  function aoTecla(e: KeyboardEvent<HTMLDivElement>) {
    // Teclas dentro de um campo ou botão da linha são delas, não da grelha.
    const alvo = e.target as HTMLElement;
    if (alvo.closest('input, button, select, textarea, a')) return;

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        mover(1);
        break;
      case 'ArrowUp':
        e.preventDefault();
        mover(-1);
        break;
      case 'Home':
        e.preventDefault();
        mover(-chaves.length);
        break;
      case 'End':
        e.preventDefault();
        mover(chaves.length);
        break;
      case 'PageDown':
        e.preventDefault();
        if (selecionada && chaves.indexOf(selecionada) === chaves.length - 1) irPara(paginaAtual + 1);
        else mover(12);
        break;
      case 'PageUp':
        e.preventDefault();
        if (selecionada && chaves.indexOf(selecionada) === 0) irPara(paginaAtual - 1);
        else mover(-12);
        break;
      case 'Enter': {
        const l = pagina_.find((x) => chave(x) === selecionada);
        if (l && aoAbrir) {
          e.preventDefault();
          aoAbrir(l);
        }
        break;
      }
      default:
    }
  }

  // ---- render ----------------------------------------------------------------
  const nCol = visiveis.length;
  let grupoAnterior: string | null = null;

  return (
    <div className="grelha">
      <div
        ref={corpo}
        className="grelha-corpo"
        tabIndex={0}
        onKeyDown={aoTecla}
        style={{ maxHeight: altura, minHeight: 200, overflow: 'auto', outline: 'none' }}
      >
        <table className="grelha-tabela" style={{ minWidth: larguraMinima }}>
          <thead>
            <tr>
              {visiveis.map((c) => {
                const ativa = ordenacao?.coluna === c.id;
                return (
                  <th
                    key={c.id}
                    style={{
                      width: c.largura,
                      textAlign: c.alinhar ?? 'left',
                      cursor: podeOrdenar(c) ? 'pointer' : 'default',
                      userSelect: 'none',
                    }}
                    onClick={() => ordenarPor(c)}
                    title={podeOrdenar(c) ? 'Ordenar' : undefined}
                    aria-sort={ativa ? (ordenacao!.direcao === 'asc' ? 'ascending' : 'descending') : undefined}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      {c.titulo}
                      {ativa &&
                        (ordenacao!.direcao === 'asc' ? (
                          <IconSortAscending size={13} style={{ color: 'var(--erp-dourado-escuro)' }} />
                        ) : (
                          <IconSortDescending size={13} style={{ color: 'var(--erp-dourado-escuro)' }} />
                        ))}
                    </span>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {pagina_.length === 0 && (
              <tr>
                <td colSpan={nCol} className="grelha-vazia">
                  {carregando ? 'A carregar…' : (vazio ?? 'Sem registos.')}
                </td>
              </tr>
            )}
            {pagina_.map((l) => {
              const k = chave(l);
              const g = grupo ? grupo(l) : null;
              const cabecaGrupo = g != null && g !== grupoAnterior;
              grupoAnterior = g;
              return (
                <Fragment key={k}>
                  {cabecaGrupo && (
                    <tr className="grelha-grupo">
                      <td colSpan={nCol}>{g}</td>
                    </tr>
                  )}
                  <tr
                    data-chave={k}
                    className={selecionada === k ? 'grelha-linha grelha-selecionada' : 'grelha-linha'}
                    onClick={() => setSelecionada(k)}
                    onDoubleClick={(e) => {
                      if ((e.target as HTMLElement).closest('button, a, input')) return;
                      aoAbrir?.(l);
                    }}
                  >
                    {visiveis.map((c) => (
                      <td
                        key={c.id}
                        style={{
                          textAlign: c.alinhar ?? 'left',
                          whiteSpace: c.semQuebra ? 'nowrap' : undefined,
                          fontVariantNumeric: c.alinhar === 'right' ? 'tabular-nums' : undefined,
                        }}
                      >
                        {c.render ? c.render(l) : (c.valor?.(l) ?? '—')}
                      </td>
                    ))}
                  </tr>
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* ---- rodapé: contagem, páginas, colunas ------------------------------ */}
      <div className="grelha-rodape">
        <Group gap="md" wrap="wrap" style={{ flex: 1 }}>
          <Text size="xs" c="dimmed">
            {total === 0 ? 'Sem registos' : `${primeiro}–${ultimo} de ${total}`}
          </Text>
          {rodape}
        </Group>

        <Group gap={4} wrap="nowrap">
          <Select
            size="xs"
            w={68}
            data={TAMANHOS}
            value={String(porPagina)}
            onChange={(v) => v && mudarTamanho(Number(v))}
            allowDeselect={false}
            title="Registos por página"
          />
          <ActionIcon
            variant="subtle"
            color="gray"
            size="sm"
            disabled={paginaAtual <= 1}
            onClick={() => irPara(paginaAtual - 1)}
            aria-label="Página anterior"
          >
            <IconChevronLeft size={15} />
          </ActionIcon>
          <Text size="xs" style={{ minWidth: 54, textAlign: 'center' }}>
            {paginaAtual} / {totalPaginas}
          </Text>
          <ActionIcon
            variant="subtle"
            color="gray"
            size="sm"
            disabled={paginaAtual >= totalPaginas}
            onClick={() => irPara(paginaAtual + 1)}
            aria-label="Página seguinte"
          >
            <IconChevronRight size={15} />
          </ActionIcon>

          <Menu shadow="md" width={230} closeOnItemClick={false}>
            <Menu.Target>
              <Tooltip label="Escolher colunas">
                <ActionIcon variant="subtle" color="gray" size="sm" aria-label="Colunas">
                  <IconColumns3 size={15} />
                </ActionIcon>
              </Tooltip>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Label>Colunas visíveis</Menu.Label>
              {colunas.map((c) => (
                <Menu.Item key={c.id} disabled={c.fixa}>
                  <Checkbox
                    size="xs"
                    label={c.titulo}
                    checked={!escondidas.has(c.id)}
                    disabled={c.fixa}
                    onChange={(e) => {
                      const n = new Set(escondidas);
                      if (e.currentTarget.checked) n.delete(c.id);
                      else n.add(c.id);
                      setEscondidas(n);
                    }}
                  />
                </Menu.Item>
              ))}
            </Menu.Dropdown>
          </Menu>
        </Group>
      </div>
    </div>
  );
}
