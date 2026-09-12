import type maplibregl from 'maplibre-gl';

/**
 * Fundos do mapa sem chave paga.
 *
 * O satélite da ESRI é o que dá a leitura de terreno que interessa a quem gere
 * obras — ver o parque, a pedreira, o acesso. O mapa de ruas serve para
 * circulação urbana.
 *
 * <p>Vive aqui, fora de qualquer ecrã, porque é usado tanto pelo mapa ao vivo
 * como pelo seletor de pontos. Dois sítios com listas de tiles diferentes seria
 * a receita para um deles deixar de carregar sem ninguém dar por isso.
 */
export const BASEMAPS = {
  satelite: {
    label: 'Satélite',
    tiles: [
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    ],
    attribution: 'Imagens © Esri, Maxar, Earthstar Geographics',
    maxzoom: 19,
  },
  ruas: {
    label: 'Ruas',
    tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
    attribution: '© OpenStreetMap',
    maxzoom: 19,
  },
  /*
   * Fundo escuro para vigiar de noite.
   *
   * O satélite é fotografia tirada de dia — sempre, em qualquer fornecedor.
   * Não existe imagem de satélite ao vivo à venda. Às três da manhã, um mapa
   * que mostra sol é uma mentira sobre a hora e cansa a vista de quem está a
   * olhar para ele há horas.
   */
  noite: {
    label: 'Noite',
    // O fundo escuro do CARTO passou a exigir chave: os tiles vinham com
    // «API KEY REQUIRED» por cima. O cinzento-escuro da ESRI não pede chave,
    // como o satélite. Os rótulos vêm numa camada à parte, por cima.
    tiles: [
      'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
    ],
    rotulos: [
      'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}',
    ],
    attribution: '© Esri, HERE, Garmin, © OpenStreetMap contributors',
    maxzoom: 16,
  },
} as const;

export type Basemap = keyof typeof BASEMAPS;

/** Luanda, para quando ainda não há nada no mapa. */
export const CENTRO_OMISSAO: [number, number] = [13.2344, -8.8383];

export function styleFor(key: Basemap): maplibregl.StyleSpecification {
  const b = BASEMAPS[key] as (typeof BASEMAPS)[Basemap] & { rotulos?: readonly string[] };
  const sources: maplibregl.StyleSpecification['sources'] = {
    base: {
      type: 'raster',
      tiles: [...b.tiles],
      tileSize: 256,
      attribution: b.attribution,
      maxzoom: b.maxzoom,
    },
  };
  const layers: maplibregl.StyleSpecification['layers'] = [{ id: 'base', type: 'raster', source: 'base' }];
  if (b.rotulos) {
    sources.rotulos = { type: 'raster', tiles: [...b.rotulos], tileSize: 256, maxzoom: b.maxzoom };
    layers.push({ id: 'rotulos', type: 'raster', source: 'rotulos' });
  }
  return { version: 8, sources, layers };
}
