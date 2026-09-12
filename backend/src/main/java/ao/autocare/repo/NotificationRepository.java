package ao.autocare.repo;

import ao.autocare.domain.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            String userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(String userId);

    /** Já existe um aviso desta origem para esta pessoa? Evita repetições. */
    Optional<Notification> findByUserIdAndSourceKindAndSourceId(
            String userId, String sourceKind, String sourceId);

    @Modifying
    @Query("update Notification n set n.readAt = :now "
            + "where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") String userId, @Param("now") java.time.Instant now);

    /**
     * Apaga os avisos de uma origem que deixou de se verificar (stock reposto,
     * tarefa executada, documento renovado). Sem isto o índice de origem única
     * impediria para sempre um aviso novo quando o problema voltasse a acontecer.
     *
     * <p>Apanha tanto a origem simples ({@code <id>}) como as compostas por
     * marco ({@code <id>:30}, {@code <id>:7}), que os avisos de validade usam
     * para poderem avisar várias vezes ao longo do tempo sem se repetirem.
     */
    @Modifying
    @Query("delete from Notification n where n.sourceKind = :kind "
            + "and (n.sourceId = :id or n.sourceId like concat(:id, ':%'))")
    int deleteBySource(@Param("kind") String sourceKind, @Param("id") String sourceId);
}
