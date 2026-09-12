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
}
