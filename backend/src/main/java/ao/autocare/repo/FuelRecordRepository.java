package ao.autocare.repo;

import ao.autocare.domain.FuelRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FuelRecordRepository extends JpaRepository<FuelRecord, String> {

    Page<FuelRecord> findByAssetIdOrderByFilledAtDesc(String assetId, Pageable pageable);

    Optional<FuelRecord> findByIdAndOrganizationId(String id, String organizationId);

    /** Toda a empresa, do mais recente para o mais antigo. Para relatorios. */
    Page<FuelRecord> findByOrganizationIdOrderByFilledAtDesc(
            String organizationId, Pageable pageable);

    /**
     * Último enchimento completo antes de uma data. É o ponto de partida do
     * cálculo de consumo: entre dois depósitos cheios sabe-se exatamente quanto
     * foi gasto.
     *
     * <p>O {@code excludeId} não é redundante com a data, e a diferença já
     * custou um defeito silencioso: o {@code Instant} em memória tem
     * nanossegundos e a coluna guarda menos precisão, por isso um registo
     * acabado de gravar aparecia como sendo <b>anterior a si próprio</b>.
     * O consumo saía zero e a deteção de medidor a recuar nunca disparava —
     * sem erro nenhum, apenas números em falta. A identidade é exata; o
     * instante não é.
     */
    @Query("""
            select f from FuelRecord f
            where f.asset.id = :assetId and f.fullTank = true
              and f.filledAt <= :before
              and (:excludeId is null or f.id <> :excludeId)
            order by f.filledAt desc
            limit 1
            """)
    Optional<FuelRecord> lastFullTankBefore(String assetId, Instant before, String excludeId);

    /** Consumos já calculados de um ativo, do mais recente para o mais antigo. */
    @Query("""
            select f from FuelRecord f
            where f.asset.id = :assetId and f.consumption is not null
            order by f.filledAt desc
            """)
    List<FuelRecord> withConsumption(String assetId, Pageable pageable);

    @Query("""
            select f from FuelRecord f join fetch f.asset a
            where f.organization.id = :orgId and f.filledAt >= :from and f.filledAt < :to
            order by f.filledAt desc
            """)
    List<FuelRecord> forOrgBetween(String orgId, Instant from, Instant to);

    /** Abastecimentos de um ativo numa janela -- usado para apanhar duplicados. */
    @Query("""
            select f from FuelRecord f
            where f.asset.id = :assetId and f.filledAt >= :from and f.filledAt <= :to
            order by f.filledAt asc
            """)
    List<FuelRecord> betweenForAsset(String assetId, Instant from, Instant to);

    /** Abastecimentos de um ativo no periodo, para as contas de custo. */
    @Query("""
            select f from FuelRecord f
            where f.asset.id = :assetId and f.filledAt >= :from and f.filledAt < :to
            order by f.filledAt asc
            """)
    List<FuelRecord> forAssetBetween(String assetId, Instant from, Instant to);

    /** Lançamentos de uma origem numa janela de tempo — para cruzar sensor com manual. */
    List<FuelRecord> findByAssetIdAndSourceAndFilledAtBetween(
            String assetId, ao.autocare.domain.enums.Enums.FuelSource source,
            Instant from, Instant to);
}
