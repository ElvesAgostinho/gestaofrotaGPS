package ao.autocare.repo;

import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FuelAnomalyRepository extends JpaRepository<FuelAnomaly, String> {

    Optional<FuelAnomaly> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByFuelRecordIdAndKind(String fuelRecordId, FuelAnomalyKind kind);

    Page<FuelAnomaly> findByOrganizationIdOrderByOccurredAtDesc(
            String organizationId, Pageable pageable);

    Page<FuelAnomaly> findByOrganizationIdAndStatusOrderByOccurredAtDesc(
            String organizationId, AnomalyStatus status, Pageable pageable);

    List<FuelAnomaly> findByAssetIdOrderByOccurredAtDesc(String assetId);

    /** As anomalias de um abastecimento concreto. */
    List<FuelAnomaly> findByFuelRecordId(String fuelRecordId);

    /**
     * Dinheiro por explicar no periodo.
     *
     * <p>So conta o que esta por analisar ou confirmado: o que foi dispensado
     * tinha explicacao, e somar isso inflacionaria o numero que a direccao ve.
     */
    @Query("""
            select coalesce(sum(a.costAtRisk), 0) from FuelAnomaly a
            where a.organization.id = :organizationId
              and a.status in (ao.autocare.domain.enums.Enums$AnomalyStatus.OPEN,
                               ao.autocare.domain.enums.Enums$AnomalyStatus.CONFIRMED)
              and a.occurredAt >= :inicio and a.occurredAt < :fim
            """)
    java.math.BigDecimal costAtRiskBetween(
            String organizationId, Instant inicio, Instant fim);

    @Query("""
            select coalesce(sum(a.litersAtRisk), 0) from FuelAnomaly a
            where a.organization.id = :organizationId
              and a.status in (ao.autocare.domain.enums.Enums$AnomalyStatus.OPEN,
                               ao.autocare.domain.enums.Enums$AnomalyStatus.CONFIRMED)
              and a.occurredAt >= :inicio and a.occurredAt < :fim
            """)
    java.math.BigDecimal litersAtRiskBetween(
            String organizationId, Instant inicio, Instant fim);

    @Query("""
            select a from FuelAnomaly a
            join fetch a.asset
            where a.organization.id = :organizationId
              and a.occurredAt >= :inicio and a.occurredAt < :fim
            order by a.occurredAt desc
            """)
    List<FuelAnomaly> between(String organizationId, Instant inicio, Instant fim);

    long countByOrganizationIdAndStatus(String organizationId, AnomalyStatus status);

    /** Já há uma anomalia deste tipo neste ativo depois desta hora? Evita repetir a sangria a cada posição. */
    boolean existsByAssetIdAndKindAndOccurredAtAfter(
            String assetId, ao.autocare.domain.enums.Enums.FuelAnomalyKind kind,
            java.time.Instant after);
}
