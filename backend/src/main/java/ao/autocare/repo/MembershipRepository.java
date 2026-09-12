package ao.autocare.repo;

import ao.autocare.domain.Membership;
import ao.autocare.domain.enums.Enums.MembershipRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, String> {

    List<Membership> findByUserId(String userId);

    /** Organização principal do utilizador — a primeira a que aderiu. */
    Optional<Membership> findFirstByUserIdOrderByCreatedAtAsc(String userId);

    Optional<Membership> findByUserIdAndOrganizationId(String userId, String organizationId);

    /** Equipa de uma empresa, por ordem de entrada. */
    @Query("select m from Membership m join fetch m.user where m.organization.id = :organizationId "
            + "order by m.createdAt asc")
    List<Membership> findTeam(@Param("organizationId") String organizationId);

    Optional<Membership> findByIdAndOrganizationId(String id, String organizationId);

    long countByOrganizationIdAndRoleAndSuspendedAtIsNull(String organizationId, MembershipRole role);

    /** Organização ativa do utilizador — ignora as adesões suspensas. */
    Optional<Membership> findFirstByUserIdAndSuspendedAtIsNullOrderByCreatedAtAsc(String userId);
}
