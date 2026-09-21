export interface OrganizationView {
  id: string;
  name: string;
  type: string;
  myRole: string;
  assetCount: number;
  assetTypeCount: number;
  locationCount: number;
  memberCount: number;
}

export type CriticalityLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type MeterKind = 'HOURMETER' | 'ODOMETER';
export type AssetStatus = 'OPERATIONAL' | 'MAINTENANCE' | 'DOWN' | 'STANDBY' | 'RETIRED';
export type PhotoKind = 'GENERAL' | 'PLATE' | 'DAMAGE' | 'DOCUMENT' | 'METER';

export interface MeterView {
  id: string;
  kind: MeterKind;
  unit: string;
  currentValue: number;
  dailyAverage: number | null;
  lastReadingAt: string | null;
  primary: boolean;
}

export interface CriticalityView {
  productionImpact: number;
  safetyImpact: number;
  financialImpact: number;
  overall: CriticalityLevel;
  overallManual: boolean;
  assessedAt: string | null;
  notes: string | null;
}

export interface AssetPhotoView {
  id: string;
  url: string;
  kind: PhotoKind;
  caption: string | null;
  primary: boolean;
  sortOrder: number;
  contentType: string;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  originalName: string | null;
  createdAt: string;
}

export interface AssetSummary {
  /** A família do catálogo (BUS, TRUCK_HEAVY…), decidida pelo servidor. */
  family?: string | null;
  /** Família do ativo: viaturas, máquinas, geradores. Serve para agrupar. */
  category?: string | null;
  categoryLabel?: string | null;
  categoryOrder?: number | null;
  id: string;
  tag: string;
  name: string;
  assetTypeName: string;
  locationName: string | null;
  status: AssetStatus;
  criticality: CriticalityLevel;
  archived: boolean;
  primaryPhotoUrl: string | null;
  photoCount: number;
  meters: MeterView[];
  /** A tarefa de manutenção que vence primeiro; nulo sem plano. */
  nextMaintenance?: {
    title: string;
    status: 'OK' | 'DUE_SOON' | 'OVERDUE' | string;
    remainingMeter?: number | null;
    meterKind?: string | null;
    remainingDays?: number | null;
    nextDueMeter?: number | null;
    nextDueAt?: string | null;
  } | null;
}

export interface AssetView {
  id: string;
  tag: string;
  name: string;
  assetTypeId: string;
  assetTypeName: string;
  /** A família decidida pelo servidor: RETROESCAVADORA, TRUCK_HEAVY, LIGHT_VEHICLE, GENERATOR. */
  family?: string | null;
  locationId: string | null;
  locationName: string | null;
  responsibleUserId: string | null;
  responsibleLabel: string | null;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  modelYear: number | null;
  plate: string | null;
  acquisitionDate: string | null;
  acquisitionValue: number | null;
  currency: string;
  objective: string | null;
  notes: string | null;
  status: AssetStatus;
  archived: boolean;
  createdAt: string;
  primaryPhotoUrl: string | null;
  meters: MeterView[];
  criticality: CriticalityView;
  photos: AssetPhotoView[];
  latitude: number | null;
  longitude: number | null;
  positionAt: string | null;
  positionSource: string | null;
  speedLimitKph: number | null;
  tankCapacityLiters?: number | null;
  /** Só chega a quem pode ver custos. */
  downtimeCostPerHour?: number | null;
  /** Versão do registo, devolvida ao gravar para apanhar edições concorrentes. */
  version?: number | null;
  /** Último nível do depósito lido pelo sensor do GPS. */
  fuelLevelLiters?: number | null;
  fuelLevelAt?: string | null;
  /** Quando saiu da frota; ausente enquanto estiver ao serviço. */
  retiredAt?: string | null;
  retiredReason?: string | null;
}

export interface AssetTypeView {
  id: string;
  name: string;
  category: string;
  primaryMeter: MeterKind;
  secondaryMeter: MeterKind | null;
  icon: string | null;
  assetCount: number;
  systems: { id: string; code: string; name: string; sortOrder: number }[];
}

export interface LocationView {
  id: string;
  name: string;
  code: string | null;
  kind: string;
  parentId: string | null;
  parentName: string | null;
  active: boolean;
  notes: string | null;
  assetCount: number;
}

export interface LocationNode {
  id: string;
  name: string;
  code: string | null;
  kind: string;
  active: boolean;
  children: LocationNode[];
}

export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
