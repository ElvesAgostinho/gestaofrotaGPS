/** O que a API dos fornecedores devolve. */

export interface Fornecedor {
  id: string;
  name: string;
  taxId?: string | null;
  kind: 'WORKSHOP' | 'PARTS' | 'BOTH' | 'OTHER';
  kindLabel: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  city?: string | null;
  contactPerson?: string | null;
  paymentTerms?: string | null;
  defaultWarrantyMonths?: number | null;
  notes?: string | null;
  active: boolean;

  vatRegime?: string | null;
  withholdingTax?: number | null;
  iban?: string | null;
  bankName?: string | null;
  currency?: string | null;
  avgLeadTimeDays?: number | null;
  importer: boolean;
  incoterm?: string | null;
  customsDays?: number | null;
  minOrderValue?: number | null;
  brands?: string | null;
  families?: string | null;
  /** Trânsito mais despacho: o prazo que conta para decidir quando encomendar. */
  prazoTotalDias?: number | null;
}

export interface Contacto {
  id: string;
  name: string;
  role: 'SALES' | 'FINANCE' | 'WAREHOUSE' | 'TECHNICAL' | 'OTHER';
  roleLabel: string;
  phone?: string | null;
  email?: string | null;
  note?: string | null;
  primaryContact: boolean;
}

export interface Documento {
  id: string;
  kind: string;
  kindLabel: string;
  number?: string | null;
  issuedAt?: string | null;
  expiresAt?: string | null;
  fileId?: string | null;
  note?: string | null;
  diasAteExpirar?: number | null;
  estado: 'VALIDO' | 'A_EXPIRAR' | 'EXPIRADO' | 'SEM_VALIDADE';
}

export interface Preco {
  id: string;
  supplierId: string;
  supplierName: string;
  partId: string;
  partName: string;
  partNumber?: string | null;
  supplierPartNumber?: string | null;
  price: number;
  currency: string;
  minOrderQuantity?: number | null;
  leadTimeDays?: number | null;
  quotedAt?: string | null;
  validUntil?: string | null;
  expirada: boolean;
  note?: string | null;
}

export const TIPOS_FORNECEDOR = [
  { value: 'PARTS', label: 'Peças' },
  { value: 'WORKSHOP', label: 'Oficina' },
  { value: 'BOTH', label: 'Oficina e peças' },
  { value: 'OTHER', label: 'Outro' },
];

export const REGIMES_IVA = [
  { value: 'GERAL', label: 'Regime geral' },
  { value: 'SIMPLIFICADO', label: 'Regime simplificado' },
  { value: 'EXCLUSAO', label: 'Regime de exclusão' },
  { value: 'ISENTO', label: 'Isento' },
];

export const TIPOS_DOCUMENTO = [
  { value: 'ALVARA', label: 'Alvará comercial' },
  { value: 'CERTIDAO_NAO_DIVIDA', label: 'Certidão de não dívida' },
  { value: 'DECLARACAO_INICIO', label: 'Declaração de início de actividade' },
  { value: 'NIF', label: 'Cartão de contribuinte' },
  { value: 'SEGURO', label: 'Seguro' },
  { value: 'CONTRATO', label: 'Contrato de fornecimento' },
  { value: 'OUTRO', label: 'Outro documento' },
];

export const PAPEIS_CONTACTO = [
  { value: 'SALES', label: 'Comercial' },
  { value: 'FINANCE', label: 'Financeiro' },
  { value: 'WAREHOUSE', label: 'Armazém' },
  { value: 'TECHNICAL', label: 'Técnico' },
  { value: 'OTHER', label: 'Outro' },
];

export const COR_ESTADO_DOC: Record<Documento['estado'], string> = {
  VALIDO: 'green',
  A_EXPIRAR: 'orange',
  EXPIRADO: 'red',
  SEM_VALIDADE: 'gray',
};

export const ROTULO_ESTADO_DOC: Record<Documento['estado'], string> = {
  VALIDO: 'Válido',
  A_EXPIRAR: 'A expirar',
  EXPIRADO: 'Expirado',
  SEM_VALIDADE: 'Sem validade',
};
