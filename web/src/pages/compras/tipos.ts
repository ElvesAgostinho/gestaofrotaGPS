/** O que a API das compras devolve. */

export type EstadoOrdem =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'SENT'
  | 'CONFIRMED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CLOSED'
  | 'CANCELLED';

export interface LinhaOrdem {
  id?: string;
  partId?: string | null;
  description: string;
  specification?: string | null;
  supplierPartNumber?: string | null;
  quantity: number;
  unit?: string | null;
  unitPrice: number;
  lineTotal: number;
  assetId?: string | null;
  assetTag?: string | null;
  receivedQuantity?: number;
  porReceber?: number;
}

export interface EventoOrdem {
  kind: string;
  rotulo: string;
  note?: string | null;
  byLabel?: string | null;
  at: string;
}

export interface Podem {
  editar: boolean;
  submeter: boolean;
  aprovar: boolean;
  rejeitar: boolean;
  enviar: boolean;
  confirmar: boolean;
  anular: boolean;
  porqueNaoAprova?: string | null;
}

export interface Ordem {
  id: string;
  number: string;
  supplierId?: string | null;
  supplierName: string;
  supplierTaxId?: string | null;
  supplierEmail?: string | null;
  supplierIban?: string | null;
  supplierBank?: string | null;
  paymentTerms?: string | null;
  status: EstadoOrdem;
  statusLabel: string;
  currency: string;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  withholdingAmount: number;
  total: number;
  payable: number;
  expectedAt?: string | null;
  deliveryAddress?: string | null;
  notes?: string | null;
  createdByLabel?: string | null;
  createdAt?: string | null;
  approvedByLabel?: string | null;
  approvedAt?: string | null;
  rejectedReason?: string | null;
  sentAt?: string | null;
  sentTo?: string | null;
  confirmedAt?: string | null;
  lines: LinhaOrdem[];
  eventos: EventoOrdem[];
  podem: Podem;
  quemAprova?: string | null;
  impactoNoOrcamento?: string | null;
}

export interface ConfiguracaoCompras {
  autoApproveBelow?: number | null;
  ownerApprovalAbove?: number | null;
  currency?: string | null;
  deliveryAddress?: string | null;
  deliveryContact?: string | null;
  terms?: string | null;
}

/**
 * As cores dos estados.
 *
 * <p>«Por aprovar» é laranja porque está à espera de alguém; «rejeitada» é
 * vermelha porque exige trabalho; «enviada» é azul e «confirmada» verde,
 * porque só a segunda quer dizer que o fornecedor leu.
 */
export const COR_ESTADO: Record<EstadoOrdem, string> = {
  DRAFT: 'gray',
  PENDING_APPROVAL: 'orange',
  APPROVED: 'teal',
  REJECTED: 'red',
  SENT: 'blue',
  CONFIRMED: 'green',
  PARTIALLY_RECEIVED: 'lime',
  RECEIVED: 'green',
  CLOSED: 'gray',
  CANCELLED: 'gray',
};
