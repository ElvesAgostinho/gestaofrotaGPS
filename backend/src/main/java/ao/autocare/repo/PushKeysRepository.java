package ao.autocare.repo;

import ao.autocare.domain.PushKeys;
import org.springframework.data.jpa.repository.JpaRepository;

/** Uma linha só: as chaves desta instalação. */
public interface PushKeysRepository extends JpaRepository<PushKeys, String> {
}
