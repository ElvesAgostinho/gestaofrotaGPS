package ao.autocare.repo;

import ao.autocare.domain.GpsDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GpsDeviceRepository extends JpaRepository<GpsDevice, String> {

    List<GpsDevice> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    Optional<GpsDevice> findByIdAndOrganizationId(String id, String organizationId);

    Optional<GpsDevice> findByOrganizationIdAndExternalId(String organizationId, String externalId);

    /** Usado na ingestão: o aparelho identifica-se só pelo seu id externo. */
    List<GpsDevice> findByExternalId(String externalId);

    Optional<GpsDevice> findFirstByAssetId(String assetId);

    long countByOrganizationId(String organizationId);

    /** Este ativo tem aparelho instalado? Se tem, o contador anda sozinho. */
    boolean existsByAssetId(String assetId);
}
