import type { Role } from '../../auth/AuthContext';

/** Onde se guarda a escolha «telemóvel» / «versão completa» deste aparelho. */
export const MODO_KEY = 'imbondeiro.modo';

/**
 * Quem deve cair na app do telemóvel ao entrar: o motorista sempre (o resto
 * do sistema não é para ele); o mecânico quando está num ecrã pequeno. A
 * escolha explícita da pessoa («Versão completa» / «Modo telemóvel») ganha.
 */
/**
 * A aplicação do telemóvel é de quem trabalha na estrada e na oficina.
 *
 * <p>O motorista tem lá a rota, a inspeção e o abastecimento; o mecânico tem
 * as ordens. Um dono ou um gestor não tem lá nada que seja seu — o que veria é
 * a área de outra pessoa, com «a sua viatura» e «a sua rota» a apontar para
 * ninguém. Para esses, o sistema de gestão é o único ecrã, no computador ou
 * no telemóvel.
 */
export function temAppDoTelemovel(role?: Role): boolean {
  return role === 'DRIVER' || role === 'TECHNICIAN';
}

export function prefereTelemovel(role?: Role): boolean {
  if (!temAppDoTelemovel(role)) return false;
  let escolha: string | null = null;
  try {
    escolha = localStorage.getItem(MODO_KEY);
  } catch {
    escolha = null;
  }
  if (escolha === 'desktop') return false;
  if (escolha === 'mobile') return true;
  if (role === 'DRIVER') return true;
  return window.matchMedia('(max-width: 768px)').matches;
}
