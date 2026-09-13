import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokens } from '../api/client';

export interface SessionUser {
  id: string;
  name: string;
  email?: string;
  /** Telemóvel em formato internacional; para onde vão os avisos por WhatsApp/SMS. */
  phone?: string | null;
  admin: boolean;
}

export interface Organization {
  id: string;
  name: string;
  myRole: Role;
  /** Códigos das permissões efetivas, calculados pelo servidor. */
  myPermissions?: string[];
  taxId?: string | null;
  address?: string | null;
  city?: string | null;
  phone?: string | null;
  email?: string | null;
  logoUrl?: string | null;
  assetCount: number;
  memberCount: number;
  defaultSpeedLimitKph?: number;
  /** Último dia da licença (nulo = sem prazo). */
  licenseUntil?: string | null;
  /** Frase do servidor quando a empresa está suspensa ou a licença venceu. */
  blockedReason?: string | null;
}

export type Role = 'OWNER' | 'MANAGER' | 'TECHNICIAN' | 'DRIVER' | 'VIEWER';

// A mesma hierarquia que o backend aplica. Aqui serve só para esconder o que a
// pessoa não pode fazer — quem decide continua a ser o servidor, e um ecrã que
// mostre a mais dá um 403 em vez de deixar passar.
const RANK: Record<Role, number> = {
  OWNER: 40,
  MANAGER: 30,
  TECHNICIAN: 20,
  DRIVER: 20,
  VIEWER: 10,
};

interface AuthState {
  user: SessionUser | null;
  org: Organization | null;
  loading: boolean;
  /** Verdadeiro se o papel na empresa cobre o exigido. */
  can: (required: Role) => boolean;
  /**
   * Verdadeiro se tenho esta permissão de módulo (ex.: 'COSTS_VIEW').
   *
   * <p>Serve para esconder o que a pessoa não pode fazer; quem decide
   * continua a ser o servidor. Um ecrã que mostre a mais dá um 403 em vez de
   * deixar passar.
   */
  has: (permission: string) => boolean;
  refresh: () => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [org, setOrg] = useState<Organization | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!tokens.access) {
      setUser(null);
      setOrg(null);
      setLoading(false);
      return;
    }
    try {
      // As duas coisas em paralelo: a conta e a empresa onde ela trabalha.
      const [me, organization] = await Promise.all([
        api<SessionUser>('/auth/me'),
        api<Organization>('/organization').catch(() => null),
      ]);
      setUser(me);
      setOrg(organization);
    } catch {
      tokens.clear();
      setUser(null);
      setOrg(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const signOut = useCallback(() => {
    tokens.clear();
    setUser(null);
    setOrg(null);
  }, []);

  useEffect(() => {
    // Quando o cliente HTTP desiste de renovar a sessão, o ecrã tem de saber.
    setUnauthorizedHandler(signOut);
    void load();
  }, [load, signOut]);

  const value = useMemo<AuthState>(
    () => ({
      user,
      org,
      loading,
      can: (required) => {
        const mine = org?.myRole;
        return !!mine && RANK[mine] >= RANK[required];
      },
      has: (permission) => !!user?.admin || (org?.myPermissions ?? []).includes(permission),
      refresh: load,
      signOut,
    }),
    [user, org, loading, load, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth tem de ser usado dentro de <AuthProvider>');
  return ctx;
}
