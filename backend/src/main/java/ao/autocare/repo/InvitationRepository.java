package ao.autocare.repo;

import ao.autocare.domain.Invitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, String> {

    List<Invitation> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByIdAndOrganizationId(String id, String organizationId);

    /** Convite ainda por aceitar para o mesmo email na mesma empresa. */
    Optional<Invitation> findFirstByOrganizationIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
            String organizationId, String email);
}
