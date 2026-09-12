package ao.autocare.repo;

import ao.autocare.domain.StoredFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {

    Optional<StoredFile> findByIdAndOrganizationId(String id, String organizationId);
}
