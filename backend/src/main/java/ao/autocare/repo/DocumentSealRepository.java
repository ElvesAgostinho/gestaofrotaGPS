package ao.autocare.repo;

import ao.autocare.domain.DocumentSeal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSealRepository extends JpaRepository<DocumentSeal, String> {

    Optional<DocumentSeal> findByCode(String code);

    boolean existsByCode(String code);
}
