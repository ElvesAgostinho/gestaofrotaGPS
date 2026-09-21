package ao.autocare.security;

import ao.autocare.domain.enums.Enums.MembershipRole;
import java.util.EnumSet;
import java.util.Set;

/**
 * O que cada pessoa pode fazer, módulo a módulo.
 *
 * <p>Os quatro papéis (dono, gestor, técnico, leitor) são um ponto de partida,
 * não a última palavra. Numa empresa a sério o contabilista vê custos mas não
 * abre ordens; o chefe de oficina fecha ordens mas não vê o que custam; o
 * motorista lança combustível e mais nada. Cada permissão tem os papéis que a
 * trazem por omissão, e cada membro pode receber ou perder permissões uma a
 * uma, por cima do papel.
 *
 * <p>Os códigos são estáveis: ficam na base de dados e no token. Mudar o rótulo
 * é livre; mudar o código é uma migração.
 */
public enum Permission {

    // ---- leitura ------------------------------------------------------------
    /**
     * Ver a frota: equipamento, ordens, mapas, indicadores e relatórios.
     *
     * <p>Existe por causa de uma falha real: as permissões travavam quem
     * escreve, mas quem estivesse autenticado lia tudo. Um motorista via a
     * empresa inteira ao trocar para a versão completa — os outros veículos,
     * os custos, a equipa. Um motorista vê o que é dele; para ver a frota é
     * preciso esta permissão, que ele não tem.
     */
    FLEET_VIEW("Equipamento", "Ver a frota: equipamento, ordens, mapas e indicadores",
            MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.TECHNICIAN,
            MembershipRole.VIEWER),

    // ---- equipamento -------------------------------------------------------
    ASSETS_MANAGE("Equipamento", "Criar, editar e arquivar equipamento, tipos e locais",
            MembershipRole.OWNER, MembershipRole.MANAGER),
    GPS_MANAGE("Equipamento", "Gerir rastreadores GPS e geocercas",
            MembershipRole.OWNER, MembershipRole.MANAGER),
    PLANS_MANAGE("Manutenção", "Criar e aprovar planos de manutenção",
            MembershipRole.OWNER, MembershipRole.MANAGER),

    // ---- manutenção --------------------------------------------------------
    WORKORDERS_MANAGE("Manutenção", "Abrir e trabalhar ordens de serviço",
            MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.TECHNICIAN),
    WORKORDERS_CLOSE("Manutenção", "Verificar, fechar e anular ordens de serviço",
            MembershipRole.OWNER, MembershipRole.MANAGER),
    WORKORDERS_APPROVE("Manutenção", "Aprovar ou rejeitar ordens que pedem aprovação",
            MembershipRole.OWNER),
    PARTS_MANAGE("Manutenção", "Gerir peças, armazéns e transferências",
            MembershipRole.OWNER, MembershipRole.MANAGER),
    STOCK_MOVE("Manutenção", "Registar entradas e saídas de stock",
            MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.TECHNICIAN),
    BREAKDOWN_REPORT("Manutenção", "Comunicar avarias pelo telemóvel (abre uma ordem corretiva)",
            MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.TECHNICIAN,
            MembershipRole.DRIVER),

    // ---- custos ------------------------------------------------------------
    COSTS_VIEW("Custos", "Ver valores financeiros (custos, orçamentos, valor de aquisição)",
            MembershipRole.OWNER, MembershipRole.MANAGER),
    FUEL_RECORD("Custos", "Lançar abastecimentos",
            MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.TECHNICIAN,
            MembershipRole.DRIVER),
    FUEL_MANAGE("Custos", "Gerir combustível: anomalias, importações, painel",
            MembershipRole.OWNER, MembershipRole.MANAGER),
    REPORTS_VIEW("Custos", "Relatórios e indicadores",
            MembershipRole.OWNER, MembershipRole.MANAGER),

    // ---- transporte --------------------------------------------------------
    TRANSPORT_MANAGE("Transporte", "Emitir e acompanhar guias de transporte",
            MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.TECHNICIAN),
    DRIVERS_MANAGE("Transporte", "Gerir motoristas, atribuições e rotas",
            MembershipRole.OWNER, MembershipRole.MANAGER),

    // ---- segurança ---------------------------------------------------------
    COMMANDS_LOCK("Segurança", "Imobilizar e desbloquear viaturas",
            MembershipRole.OWNER),

    // ---- administração -----------------------------------------------------
    TEAM_MANAGE("Administração", "Convidar utilizadores e definir permissões",
            MembershipRole.OWNER),
    SETTINGS_MANAGE("Administração", "Configurações da empresa e integrações",
            MembershipRole.OWNER);

    private final String module;
    private final String label;
    private final Set<MembershipRole> defaultRoles;

    Permission(String module, String label, MembershipRole... defaults) {
        this.module = module;
        this.label = label;
        this.defaultRoles = defaults.length == 0
                ? EnumSet.noneOf(MembershipRole.class) : EnumSet.of(defaults[0], defaults);
    }

    public String module() {
        return module;
    }

    public String label() {
        return label;
    }

    public Set<MembershipRole> defaultRoles() {
        return defaultRoles;
    }

    /** As permissões que este papel traz por omissão. */
    public static Set<Permission> defaultsFor(MembershipRole role) {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        if (role == null) {
            return set;
        }
        for (Permission p : values()) {
            if (p.defaultRoles.contains(role)) {
                set.add(p);
            }
        }
        return set;
    }

    /** O código, ou {@code null} se não existir. Um código desconhecido nunca rebenta. */
    public static Permission parse(String code) {
        if (code == null) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
