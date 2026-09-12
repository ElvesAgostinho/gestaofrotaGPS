package ao.autocare.repo;

import ao.autocare.domain.TransportNote;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportNoteRepository extends JpaRepository<TransportNote, String> {

    Optional<TransportNote> findByIdAndOrganizationId(String id, String organizationId);

    Page<TransportNote> findByOrganizationIdOrderByCreatedAtDesc(
            String organizationId, Pageable pageable);

    Page<TransportNote> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            String organizationId, ao.autocare.domain.enums.Enums.TransportNoteStatus status,
            Pageable pageable);

    boolean existsByOrganizationIdAndNumber(String organizationId, String number);
}
