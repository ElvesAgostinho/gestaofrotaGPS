package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.DriverStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * Quem conduz.
 *
 * <p>Não é obrigatoriamente um utilizador do sistema, e isso é deliberado: na
 * maior parte das frotas quem conduz não tem conta nem precisa. É uma pessoa
 * com nome, número de funcionário e carta de condução. Exigir uma conta a cada
 * motorista tornaria o módulo inutilizável na empresa real.
 */
@Getter
@Setter
@Entity
@Table(name = "drivers")
public class Driver extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** Conta no AutoCare, quando esta pessoa também usa o sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Location branch;

    @Column(name = "employee_number", length = 40)
    private String employeeNumber;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 190)
    private String email;

    /** Bilhete de identidade ou equivalente. */
    @Column(name = "national_id", length = 60)
    private String nationalId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "hired_at")
    private LocalDate hiredAt;

    @Column(name = "license_number", length = 60)
    private String licenseNumber;

    /** Categorias da carta, como vêm escritas no documento (ex.: "B, C, D"). */
    @Column(name = "license_categories", length = 60)
    private String licenseCategories;

    @Column(name = "license_issued_at")
    private LocalDate licenseIssuedAt;

    @Column(name = "license_expires_at")
    private LocalDate licenseExpiresAt;

    @Column(name = "license_country", length = 60)
    private String licenseCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverStatus status = DriverStatus.ACTIVE;

    @Column(name = "photo_file_id", length = 36)
    private String photoFileId;

    @Column(length = 2000)
    private String notes;

    /**
     * Dias até a carta caducar; negativo se já caducou, nulo se não há data.
     *
     * <p>Um motorista a conduzir com a carta caducada é responsabilidade da
     * empresa, não dele — por isso isto é uma pergunta que o sistema tem de
     * saber responder sem ninguém ir ver papel nenhum.
     */
    public Long daysUntilLicenseExpiry() {
        if (licenseExpiresAt == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), licenseExpiresAt);
    }

    public boolean isLicenseExpired() {
        Long days = daysUntilLicenseExpiry();
        return days != null && days < 0;
    }

    /** Pode conduzir agora: ativo e com carta válida (ou sem carta registada). */
    public boolean canDrive() {
        return status == DriverStatus.ACTIVE && !isLicenseExpired();
    }
}
