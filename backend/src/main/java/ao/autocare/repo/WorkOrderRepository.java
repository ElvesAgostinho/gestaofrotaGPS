package ao.autocare.repo;

import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, String> {

    Optional<WorkOrder> findByIdAndOrganizationId(String id, String organizationId);

    Page<WorkOrder> findByOrganizationIdOrderByOpenedAtDesc(String organizationId, Pageable pageable);

    Page<WorkOrder> findByOrganizationIdAndStatusOrderByOpenedAtDesc(
            String organizationId, WorkOrderStatus status, Pageable pageable);

    Page<WorkOrder> findByAssetIdOrderByOpenedAtDesc(String assetId, Pageable pageable);

    /** "As minhas ordens" — o que está atribuído a um membro da equipa. */
    Page<WorkOrder> findByOrganizationIdAndAssignedToIdOrderByOpenedAtDesc(
            String organizationId, String assignedToId, Pageable pageable);

    long countByOrganizationId(String organizationId);

    long countByOrganizationIdAndAssignedToIdAndStatusIn(String organizationId, String userId,
            java.util.List<WorkOrderStatus> statuses);

    boolean existsByOrganizationIdAndNumber(String organizationId, String number);

    @Query("""
            select count(w) from WorkOrder w
            where w.organization.id = :orgId and w.openedAt >= :from and w.openedAt < :to
            """)
    long countOpenedBetween(String orgId, Instant from, Instant to);

    @Query("""
            select count(w) from WorkOrder w
            where w.organization.id = :orgId
              and w.completedAt is not null and w.completedAt >= :from and w.completedAt < :to
            """)
    long countCompletedBetween(String orgId, Instant from, Instant to);

    @Query("""
            select count(w) from WorkOrder w
            where w.asset.id = :assetId and w.type = :type
              and w.completedAt is not null and w.completedAt >= :from and w.completedAt < :to
            """)
    long countCompletedForAssetBetween(String assetId,
            ao.autocare.domain.enums.Enums.WorkOrderType type, Instant from, Instant to);

    List<WorkOrder> findByOrganizationIdAndStatusIn(String organizationId, List<WorkOrderStatus> statuses);

    /** Ordens concluídas no período, com o ativo — para as contas do orçamento. */
    @Query("""
            select w from WorkOrder w join fetch w.asset a
            where w.organization.id = :orgId
              and w.completedAt is not null and w.completedAt >= :from and w.completedAt < :to
            """)
    List<WorkOrder> completedBetween(String orgId, Instant from, Instant to);

    boolean existsByAssetIdAndTypeAndStatusIn(String assetId,
            ao.autocare.domain.enums.Enums.WorkOrderType type, List<WorkOrderStatus> statuses);

    @Query("""
            select w from WorkOrder w
            where w.organization.id = :orgId and w.downtimeStart is not null
              and w.downtimeStart < :to
              and (w.downtimeEnd is null or w.downtimeEnd > :from)
            """)
    List<WorkOrder> downtimeOverlapping(String orgId, Instant from, Instant to);

    @Query("""
            select count(w) from WorkOrder w
            where w.organization.id = :orgId and w.type = :type
              and w.openedAt >= :from and w.openedAt < :to
            """)
    long countByTypeOpenedBetween(String orgId,
            ao.autocare.domain.enums.Enums.WorkOrderType type, Instant from, Instant to);

    /**
     * Corretivas anteriores no mesmo sistema deste ativo.
     *
     * <p>Base do aviso de avaria repetida: reparar a mesma coisa tres vezes em
     * tres meses nao e manutencao, e um problema por resolver.
     */
    @org.springframework.data.jpa.repository.Query("""
            select count(w) from WorkOrder w
            where w.asset.id = :assetId
              and w.systemCode = :systemCode
              and w.type = ao.autocare.domain.enums.Enums$WorkOrderType.CORRECTIVE
              and w.openedAt >= :desde
              and (:excludeId is null or w.id <> :excludeId)
            """)
    long countCorrectiveOnSystemSince(
            String assetId, String systemCode, java.time.Instant desde, String excludeId);
}
