package ao.autocare.repo;

import ao.autocare.domain.Failure;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FailureRepository extends JpaRepository<Failure, String> {

    Page<Failure> findByAssetIdOrderByDetectedAtDesc(String assetId, Pageable pageable);

    Page<Failure> findByOrganizationIdOrderByDetectedAtDesc(String organizationId, Pageable pageable);

    java.util.Optional<Failure> findByIdAndOrganizationId(String id, String organizationId);

    java.util.Optional<Failure> findFirstByWorkOrderId(String workOrderId);

    @Query("select count(f) from Failure f where f.asset.id = :assetId "
            + "and f.detectedAt >= :from and f.detectedAt < :to")
    long countForAssetBetween(String assetId, Instant from, Instant to);

    @Query("select count(f) from Failure f where f.organization.id = :orgId "
            + "and f.detectedAt >= :from and f.detectedAt < :to")
    long countForOrgBetween(String orgId, Instant from, Instant to);

    /** As avarias da empresa desde uma data, com o ativo carregado — para prever a próxima por sistema. */
    @Query("select f from Failure f join fetch f.asset a where f.organization.id = :orgId "
            + "and f.detectedAt >= :since order by a.id, f.detectedAt")
    java.util.List<Failure> forOrgSince(String orgId, Instant since);
}
