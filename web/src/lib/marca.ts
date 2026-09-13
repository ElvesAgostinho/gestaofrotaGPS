import { useEffect, useState } from 'react';

export interface Marca {
  organizationId: string;
  name: string;
  color?: string | null;
  logoUrl?: string | null;
}

export interface ConfigPublica {
  name: string;
  tagline?: string | null;
  registrationOpen?: boolean;
  brand?: Marca | null;
}

let cache: ConfigPublica | null = null;

/**
 * A configuração pública (nome do sistema, registo aberto?) e, quando o
 * sistema é aberto pelo domínio de uma empresa, a marca dela — nome,
 * logótipo e cor — para o ecrã de entrada ser o dela.
 */
export function useConfigPublica(): ConfigPublica | null {
  const [cfg, setCfg] = useState<ConfigPublica | null>(cache);
  useEffect(() => {
    if (cache) return;
    fetch('/api/v1/config')
      .then((r) => (r.ok ? r.json() : null))
      .then((c: ConfigPublica | null) => {
        if (c) {
          cache = c;
          setCfg(c);
          if (c.brand?.name) document.title = c.brand.name;
        }
      })
      .catch(() => undefined);
  }, []);
  return cfg;
}
