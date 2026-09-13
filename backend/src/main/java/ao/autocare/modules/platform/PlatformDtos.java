package ao.autocare.modules.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos do ecrã «Plataforma» — só para o administrador da plataforma. */
public final class PlatformDtos {

    private PlatformDtos() {}

    /** Estado de uma empresa aos olhos da plataforma. */
    public enum OrganizationStatus {
        /** Pode trabalhar. */
        ACTIVE,
        /** Licença termina dentro de 30 dias. */
        EXPIRING,
        /** Licença terminou: os utilizadores estão travados. */
        EXPIRED,
        /** Suspensa pela plataforma: os utilizadores estão travados. */
        SUSPENDED
    }

    public record OrganizationRow(
            String id,
            String name,
            String taxId,
            String city,
            Instant createdAt,
            OrganizationStatus status,
            LocalDate licenseUntil,
            Instant suspendedAt,
            String suspendedReason,
            String platformNotes,
            String ownerName,
            String ownerEmail,
            long memberCount,
            long assetCount,
            long workOrderCount,
            Instant lastActivityAt) {}

    public record Summary(
            long organizations,
            long active,
            long expiring,
            long expired,
            long suspended,
            long users,
            long assets) {}

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String taxId,
            @Size(max = 120) String city,
            @NotBlank @Size(max = 120) String ownerName,
            @NotBlank @Email @Size(max = 190) String ownerEmail,
            /** Vazio = o sistema gera uma e devolve-a uma única vez. */
            @Size(min = 8, max = 100) String ownerPassword,
            LocalDate licenseUntil,
            @Size(max = 1000) String platformNotes) {}

    public record UpdateOrganizationRequest(
            @Size(max = 160) String name,
            LocalDate licenseUntil,
            /** Verdadeiro para retirar o prazo (licença sem fim). */
            Boolean clearLicense,
            @Size(max = 1000) String platformNotes) {}

    public record SuspendRequest(@Size(max = 300) String reason) {}

    /** Resposta à criação: os dados da empresa e, se foi gerada, a palavra-passe do Dono. */
    public record CreatedOrganization(
            OrganizationRow organization,
            String ownerEmail,
            /** Só vem preenchida quando o sistema a gerou. Mostra-se uma vez e não se guarda. */
            String temporaryPassword,
            /** O email já tinha conta: foi associado como Dono sem mexer na palavra-passe. */
            boolean ownerExisted) {}

    public record OwnerPasswordReset(String ownerEmail, String temporaryPassword) {}

    public record OrganizationList(List<OrganizationRow> items) {}
}
