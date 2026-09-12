package ao.autocare.repo;

import ao.autocare.domain.GpsPosition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GpsPositionRepository extends JpaRepository<GpsPosition, String> {

    Optional<GpsPosition> findFirstByAssetIdOrderByRecordedAtDesc(String assetId);

    Page<GpsPosition> findByAssetIdOrderByRecordedAtDesc(String assetId, Pageable pageable);

    /** Trajeto de um ativo num intervalo, por ordem cronológica (para desenhar no mapa). */
    @Query("""
            select p from GpsPosition p
            where p.asset.id = :assetId and p.recordedAt >= :from and p.recordedAt < :to
            order by p.recordedAt asc
            """)
    List<GpsPosition> track(String assetId, Instant from, Instant to);

    List<GpsPosition> findByTripIdOrderByRecordedAtAsc(String tripId);

    long countByOrganizationId(String organizationId);

    boolean existsByDeviceIdAndProviderPositionId(String deviceId, String providerPositionId);

    /** A última posição do ativo que trouxe nível de combustível. */
    Optional<GpsPosition> findFirstByAssetIdAndFuelLevelLitersIsNotNullOrderByRecordedAtDesc(
            String assetId);
}
