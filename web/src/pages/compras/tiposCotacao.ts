/** Pedidos de cotação, recepção, facturas e classificação. */

export interface LinhaPedido {
  id: string;
  partId?: string | null;
  description: string;
  specification?: string | null;
  quantity: number;
  unit?: string | null;
  assetTag?: string | null;
}

export interface PrecoProposta {
  lineId: string;
  unitPrice?: number | null;
  lineTotal?: number | null;
  supplierPartNumber?: string | null;
  unavailable: boolean;
  note?: string | null;
  acimaDoMelhorPct?: number | null;
  melhorDaLinha: boolean;
}

export interface Proposta {
  id: string;
  supplierId: string;
  supplierName: string;
  sentAt?: string | null;
  sentTo?: string | null;
  respondedAt?: string | null;
  declined: boolean;
  leadTimeDays?: number | null;
  validUntil?: string | null;
  paymentTerms?: string | null;
  notes?: string | null;
  total?: number | null;
  currency: string;
  linhasCotadas: number;
  completa: boolean;
  maisBarata: boolean;
  maisRapida: boolean;
  precos: PrecoProposta[];
}

export interface Pedido {
  id: string;
  number: string;
  title: string;
  status: 'OPEN' | 'AWARDED' | 'CANCELLED';
  statusLabel: string;
  dueDate?: string | null;
  notes?: string | null;
  createdByLabel?: string | null;
  createdAt?: string | null;
  awardedOrderId?: string | null;
  awardedSupplierId?: string | null;
  awardedSupplierName?: string | null;
  awardedReason?: string | null;
  awardedAt?: string | null;
  lines: LinhaPedido[];
  propostas: Proposta[];
  leitura?: string | null;
  diferenca?: number | null;
}

export interface LinhaRecepcao {
  orderLineId: string;
  description: string;
  quantity: number;
  rejectedQuantity: number;
  rejectionReason?: string | null;
}

export interface Recepcao {
  id: string;
  number: string;
  orderId: string;
  orderNumber: string;
  receivedAt: string;
  warehouseId?: string | null;
  warehouseName?: string | null;
  deliveryNote?: string | null;
  receivedByLabel?: string | null;
  notes?: string | null;
  lines: LinhaRecepcao[];
}

export interface Factura {
  id: string;
  supplierId: string;
  supplierName: string;
  orderId?: string | null;
  orderNumber?: string | null;
  number: string;
  issuedAt?: string | null;
  dueAt?: string | null;
  amount?: number | null;
  taxAmount?: number | null;
  withholdingAmount?: number | null;
  aPagar?: number | null;
  currency: string;
  status: 'PENDING' | 'PAID' | 'DISPUTED' | 'CANCELLED';
  statusLabel: string;
  paidAt?: string | null;
  fileId?: string | null;
  fileName?: string | null;
  notes?: string | null;
  diasAteVencer?: number | null;
  conferencia?: string | null;
}

export interface Medida {
  nome: string;
  valor?: number | null;
  detalhe: string;
  estado: 'BOM' | 'ATENCAO' | 'MAU' | 'SEM_DADOS';
}

export interface Classificacao {
  supplierId: string;
  supplierName: string;
  ordens: number;
  ordensRecebidas: number;
  totalComprado: number;
  moeda: string;
  medidas: Medida[];
  leitura: string;
}

export const COR_MEDIDA: Record<Medida['estado'], string> = {
  BOM: 'green',
  ATENCAO: 'orange',
  MAU: 'red',
  SEM_DADOS: 'gray',
};

export const COR_FACTURA: Record<Factura['status'], string> = {
  PENDING: 'orange',
  PAID: 'green',
  DISPUTED: 'red',
  CANCELLED: 'gray',
};
