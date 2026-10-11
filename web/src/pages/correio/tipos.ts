/** O que a API do correio devolve. */

export interface Entidade {
  tipo: 'SUPPLIER' | 'SUPPLIER_CONTACT' | 'DRIVER' | 'TEAM';
  id: string;
  nome: string;
  email: string;
  detalhe?: string | null;
  link?: string | null;
}

export interface Pessoa {
  nome?: string | null;
  email?: string | null;
  entidade?: Entidade | null;
}

export interface Pasta {
  nome: string;
  caminho: string;
  papel: string;
  total: number;
  porLer: number;
}

export interface Resumo {
  uid: number;
  assunto?: string | null;
  de?: Pessoa | null;
  para: Pessoa[];
  data?: string | null;
  lida: boolean;
  respondida: boolean;
  temAnexos: boolean;
  anexos: number;
  tamanho: number;
}

export interface Anexo {
  id: string;
  nome: string;
  tipo: string;
  tamanho: number;
  previsualizavel: boolean;
}

export interface Mensagem {
  uid: number;
  pasta: string;
  assunto?: string | null;
  de?: Pessoa | null;
  para: Pessoa[];
  cc: Pessoa[];
  data?: string | null;
  lida: boolean;
  html?: string | null;
  texto?: string | null;
  anexos: Anexo[];
}

export interface Pagina {
  pasta: string;
  pagina: number;
  tamanhoPagina: number;
  total: number;
  mensagens: Resumo[];
}

export interface EstadoCaixa {
  configurada: boolean;
  podeEnviar: boolean;
  endereco?: string | null;
  servidor?: string | null;
  mensagem?: string | null;
}

/** As cores de cada tipo de entidade. Fornecedor é o que mais aparece. */
export const COR_ENTIDADE: Record<Entidade['tipo'], string> = {
  SUPPLIER: 'indigo',
  SUPPLIER_CONTACT: 'cyan',
  DRIVER: 'teal',
  TEAM: 'grape',
};

export const ROTULO_ENTIDADE: Record<Entidade['tipo'], string> = {
  SUPPLIER: 'Fornecedor',
  SUPPLIER_CONTACT: 'Contacto',
  DRIVER: 'Motorista',
  TEAM: 'Equipa',
};

/** Iniciais para o avatar, como em qualquer cliente de correio. */
export function iniciais(nome?: string | null, email?: string | null): string {
  const base = (nome ?? email ?? '?').trim();
  const partes = base.split(/[\s@._-]+/).filter(Boolean);
  if (partes.length === 0) {
    return '?';
  }
  if (partes.length === 1) {
    return partes[0].slice(0, 2).toUpperCase();
  }
  return (partes[0][0] + partes[1][0]).toUpperCase();
}

/** Uma cor estável por endereço: a mesma pessoa tem sempre a mesma. */
export function corDoAvatar(chave?: string | null): string {
  const cores = ['blue', 'cyan', 'teal', 'green', 'orange', 'grape', 'violet', 'indigo', 'pink'];
  const texto = chave ?? '';
  let soma = 0;
  for (let i = 0; i < texto.length; i += 1) {
    soma = (soma + texto.charCodeAt(i)) % 997;
  }
  return cores[soma % cores.length];
}

/** «há 3 min», «09:42», «12 Out» — como os clientes de correio escrevem. */
export function quando(iso?: string | null): string {
  if (!iso) {
    return '';
  }
  const d = new Date(iso);
  const agora = new Date();
  const mesmoDia = d.toDateString() === agora.toDateString();
  if (mesmoDia) {
    return d.toLocaleTimeString('pt-AO', { hour: '2-digit', minute: '2-digit' });
  }
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (d.toDateString() === ontem.toDateString()) {
    return 'ontem';
  }
  if (d.getFullYear() === agora.getFullYear()) {
    return d.toLocaleDateString('pt-AO', { day: '2-digit', month: 'short' });
  }
  return d.toLocaleDateString('pt-AO', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function tamanhoLegivel(bytes: number): string {
  if (!bytes || bytes <= 0) {
    return '';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
