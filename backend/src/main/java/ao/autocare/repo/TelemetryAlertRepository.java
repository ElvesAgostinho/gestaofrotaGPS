package ao.autocare.repo;

import ao.autocare.domain.TelemetryAlert;
import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryAlertRepository extends JpaRepository<TelemetryAlert, String> {

    /** Episódio ainda a decorrer para este ativo e tipo de alerta. */
    Optional<TelemetryAlert> findFirstByAssetIdAndKindAndEndedAtIsNull(
            String assetId, TelemetryAlertKind kind);

    Optional<TelemetryAlert> findFirstByDeviceIdAndKindAndEndedAtIsNull(
            String deviceId, TelemetryAlertKind kind);

    Page<TelemetryAlert> findByOrganizationIdOrderByStartedAtDesc(
            String organizationId, Pageable pageable);

    Page<TelemetryAlert> findByOrganizationIdAndKindOrderByStartedAtDesc(
            String organizationId, TelemetryAlertKind kind, Pageable pageable);

    Page<TelemetryAlert> findByOrganizationIdAndEndedAtIsNullOrderByStartedAtDesc(
            String organizationId, Pageable pageable);

    Page<TelemetryAlert> findByAssetIdOrderByStartedAtDesc(String assetId, Pageable pageable);

    List<TelemetryAlert> findByOrganizationIdAndAcknowledgedAtIsNull(String organizationId);

    /**
     * Episodios de um tipo que comecaram dentro de um intervalo.
     *
     * <p>Usado para trazer os excessos de velocidade ja detetados para as
     * infracoes de conducao, em vez de os detetar outra vez -- duas deteccoes
     * dariam dois numeros diferentes para o mesmo excesso.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a from TelemetryAlert a
            where a.asset.id = :assetId and a.kind = :kind
              and a.startedAt >= :inicio and a.startedAt <= :fim
            order by a.startedAt asc
            """)
    List<TelemetryAlert> findByAssetIdAndKindBetween(
            String assetId,
            ao.autocare.domain.enums.Enums.TelemetryAlertKind kind,
            java.time.Instant inicio,
            java.time.Instant fim);

    long countByOrganizationIdAndEndedAtIsNull(String organizationId);
}
