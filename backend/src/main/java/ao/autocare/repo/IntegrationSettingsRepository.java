package ao.autocare.repo;

import ao.autocare.domain.IntegrationSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSettingsRepository extends JpaRepository<IntegrationSettings, String> {

    Optional<IntegrationSettings> findByOrganizationId(String organizationId);

    /** As empresas com Traccar apontado: são as que a sondagem visita. */
    java.util.List<IntegrationSettings> findByTraccarUrlIsNotNull();

    java.util.Optional<IntegrationSettings> findByTraccarForwardSecretHash(String hash);
}
