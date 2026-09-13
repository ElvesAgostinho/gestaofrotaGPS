package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "organizations")
public class Organization extends TimestampedEntity {

    @Column(nullable = false, length = 160)
    private String name;

    // ---- timbre: o que vai no cabeçalho dos impressos -----------------------

    /** NIF. */
    @Column(name = "tax_id", length = 40)
    private String taxId;

    @Column(length = 300)
    private String address;

    @Column(length = 120)
    private String city;

    @Column(length = 40)
    private String phone;

    @Column(length = 190)
    private String email;

    /** Chave do logótipo no armazenamento de ficheiros; nulo sem logótipo. */
    @Column(name = "logo_key", length = 300)
    private String logoKey;

    @Column(name = "logo_content_type", length = 80)
    private String logoContentType;

    /** Id do registo de ficheiro do logótipo: é o que o URL assinado conhece. */
    @Column(name = "logo_file_id", length = 36)
    private String logoFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationType type = OrganizationType.PERSONAL;

    /**
     * Limite de velocidade por omissão da frota, em km/h, para os ativos que não
     * tenham um limite próprio. Vazio significa sem vigilância de velocidade —
     * não se inventa um limite que a empresa não definiu.
     */
    @Column(name = "default_speed_limit_kph", precision = 6, scale = 2)
    private java.math.BigDecimal defaultSpeedLimitKph;

    /**
     * Acima deste valor, uma manutencao externa precisa de aprovacao.
     *
     * <p>Fica na empresa porque o que e caro para uma frota de cinco viaturas e
     * trivial para uma de duzentas. Nulo significa que nada exige aprovacao --
     * que e o comportamento que o sistema sempre teve.
     */
    @jakarta.persistence.Column(name = "maintenance_approval_limit", precision = 16, scale = 2)
    private java.math.BigDecimal maintenanceApprovalLimit;

    /** Só se conclui uma ordem com pelo menos uma fotografia do «depois». */
    @jakarta.persistence.Column(name = "close_requires_after_photo", nullable = false)
    private boolean closeRequiresAfterPhoto = false;

    // ---- Marca branca (gerida pelo administrador da plataforma) -----------

    /** Domínio próprio pelo qual esta empresa entra (frota.empresa.ao); nulo = o domínio geral. */
    @Column(name = "custom_domain", length = 190)
    private String customDomain;

    /** Nome que aparece no ecrã de entrada em vez de «IMBONDEIRO OS». */
    @Column(name = "brand_name", length = 80)
    private String brandName;

    /** Cor principal (#RRGGBB). */
    /** Quando o assistente de primeira utilização foi concluído ou saltado; nulo = ainda por fazer. */
    @Column(name = "onboarding_done_at")
    private java.time.Instant onboardingDoneAt;

    @Column(name = "brand_color", length = 9)
    private String brandColor;

    // ---- Licenciamento (gerido pelo administrador da plataforma) ----------

    /** Quando a plataforma suspendeu a empresa; nulo = ativa. */
    @Column(name = "suspended_at")
    private java.time.Instant suspendedAt;

    @Column(name = "suspended_reason", length = 300)
    private String suspendedReason;

    /** Último dia de validade da licença; nulo = sem prazo. */
    @Column(name = "license_until")
    private java.time.LocalDate licenseUntil;

    /** Notas internas da plataforma (contrato, contacto comercial). Nunca saem para a empresa. */
    @Column(name = "platform_notes", length = 1000)
    private String platformNotes;

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    public boolean isLicenseExpired(java.time.LocalDate today) {
        return licenseUntil != null && licenseUntil.isBefore(today);
    }

    /**
     * Motivo pelo qual os utilizadores desta empresa não podem trabalhar, ou
     * nulo se estiver tudo em ordem. É a frase que o ecrã mostra.
     */
    public String blockedReason(java.time.LocalDate today) {
        if (isSuspended()) {
            return "A conta da empresa está suspensa"
                    + (suspendedReason != null && !suspendedReason.isBlank() ? ": " + suspendedReason : "")
                    + ". Contacte o fornecedor do sistema.";
        }
        if (isLicenseExpired(today)) {
            return "A licença da empresa terminou em "
                    + licenseUntil.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ". Contacte o fornecedor do sistema para a renovar.";
        }
        return null;
    }
}
