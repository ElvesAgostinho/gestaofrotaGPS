package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.MembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "user_id"}))
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role = MembershipRole.OWNER;

    /** Cargo na empresa (ex.: "Chefe de oficina"). Livre, apenas informativo. */
    @Column(name = "job_title", length = 120)
    private String jobTitle;

    /** Suspenso: continua no histórico das ordens, mas já não consegue entrar. */
    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "invited_by", length = 36)
    private String invitedBy;

    /**
     * Permissões dadas por cima do papel, separadas por vírgula.
     *
     * <p>Texto e não tabela: são meia dúzia de códigos por pessoa, lidos a cada
     * pedido. Uma tabela de junção custaria uma consulta a mais em todos os
     * pedidos para poupar nada.
     */
    @Column(name = "permissions_granted", length = 1000)
    private String permissionsGranted;

    /** Permissões tiradas ao papel, separadas por vírgula. */
    @Column(name = "permissions_denied", length = 1000)
    private String permissionsDenied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    /** As do papel, mais as dadas, menos as tiradas. */
    public java.util.Set<ao.autocare.security.Permission> effectivePermissions() {
        java.util.Set<ao.autocare.security.Permission> set =
                java.util.EnumSet.noneOf(ao.autocare.security.Permission.class);
        set.addAll(ao.autocare.security.Permission.defaultsFor(role));
        set.addAll(parsePermissions(permissionsGranted));
        set.removeAll(parsePermissions(permissionsDenied));
        return set;
    }

    public java.util.Set<ao.autocare.security.Permission> grantedPermissions() {
        return parsePermissions(permissionsGranted);
    }

    public java.util.Set<ao.autocare.security.Permission> deniedPermissions() {
        return parsePermissions(permissionsDenied);
    }

    public void setGranted(java.util.Collection<ao.autocare.security.Permission> ps) {
        this.permissionsGranted = joinPermissions(ps);
    }

    public void setDenied(java.util.Collection<ao.autocare.security.Permission> ps) {
        this.permissionsDenied = joinPermissions(ps);
    }

    private static java.util.Set<ao.autocare.security.Permission> parsePermissions(String csv) {
        java.util.Set<ao.autocare.security.Permission> set =
                java.util.EnumSet.noneOf(ao.autocare.security.Permission.class);
        if (csv == null || csv.isBlank()) {
            return set;
        }
        for (String code : csv.split(",")) {
            // Um código que já não existe ignora-se: apagar uma permissão do
            // catálogo não pode partir o login de quem a tinha.
            ao.autocare.security.Permission p = ao.autocare.security.Permission.parse(code);
            if (p != null) {
                set.add(p);
            }
        }
        return set;
    }

    private static String joinPermissions(java.util.Collection<ao.autocare.security.Permission> ps) {
        if (ps == null || ps.isEmpty()) {
            return null;
        }
        return ps.stream().map(Enum::name).sorted().distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
