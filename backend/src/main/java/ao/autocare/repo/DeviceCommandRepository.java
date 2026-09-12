package ao.autocare.repo;

import ao.autocare.domain.DeviceCommand;
import ao.autocare.domain.enums.Enums.DeviceCommandKind;
import ao.autocare.domain.enums.Enums.DeviceCommandStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, String> {

    Optional<DeviceCommand> findByIdAndOrganizationId(String id, String organizationId);

    Page<DeviceCommand> findByOrganizationIdOrderByRequestedAtDesc(
            String organizationId, Pageable pageable);

    Page<DeviceCommand> findByAssetIdOrderByRequestedAtDesc(String assetId, Pageable pageable);

    /** Comandos do mesmo tipo por resolver para este ativo. */
    List<DeviceCommand> findByAssetIdAndKindAndStatusIn(
            String assetId, DeviceCommandKind kind, List<DeviceCommandStatus> statuses);

    /**
     * QUALQUER comando por resolver para este ativo, seja de que tipo for.
     * Um bloqueio e um desbloqueio em fila ao mesmo tempo são contraditórios:
     * o resultado passaria a depender da ordem de chegada ao aparelho.
     */
    List<DeviceCommand> findByAssetIdAndStatusIn(
            String assetId, List<DeviceCommandStatus> statuses);

    /**
     * Comandos enviados à espera de prova de execução, dentro do prazo.
     *
     * <p>O limite temporal não é um detalhe de desempenho. Sem ele, todo o
     * comando que um protocolo silencioso nunca confirma ficava a ser sondado
     * para sempre, e a lista só crescia: ao fim de meses, milhares de pedidos
     * por hora ao fornecedor por comandos que ninguém vai confirmar.
     */
    @Query("""
            select c from DeviceCommand c
            join fetch c.asset join fetch c.device join fetch c.organization
            where c.status = ao.autocare.domain.enums.Enums$DeviceCommandStatus.SENT
              and c.sentAt >= :desde
            order by c.sentAt asc
            """)
    List<DeviceCommand> awaitingConfirmation(java.time.Instant desde);

    /** Enviados há tempo de mais sem qualquer prova: já não vale a pena sondar. */
    @Query("""
            select c from DeviceCommand c
            join fetch c.asset join fetch c.device join fetch c.organization
            where c.status = ao.autocare.domain.enums.Enums$DeviceCommandStatus.SENT
              and c.sentAt < :desde
            """)
    List<DeviceCommand> unconfirmedBefore(java.time.Instant desde);

    /** Fila do agendador: aprovados à espera de a viatura estar parada. */
    @Query("""
            select c from DeviceCommand c
            join fetch c.asset join fetch c.device join fetch c.organization
            where c.status = :status
            order by c.approvedAt asc
            """)
    List<DeviceCommand> queued(DeviceCommandStatus status);

    /** Pedidos por resolver que já passaram do prazo. */
    @Query("""
            select c from DeviceCommand c join fetch c.asset
            where c.status in :statuses and c.expiresAt <= :now
            """)
    List<DeviceCommand> expired(List<DeviceCommandStatus> statuses, java.time.Instant now);

    /**
     * Último comando confirmado pelo aparelho — é ele que diz se a viatura está
     * bloqueada agora.
     *
     * <p>O estado vai como parâmetro e não como literal na consulta: comparar um
     * campo {@code @Enumerated} com {@code 'CONFIRMED'} escrito à mão não filtra,
     * e o resultado era o sistema dar por bloqueada uma viatura cujo comando
     * tinha falhado. Num bloqueio de motor, essa é uma mentira perigosa.
     */
    @Query("""
            select c from DeviceCommand c
            where c.asset.id = :assetId and c.status = :status
            order by c.confirmedAt desc
            limit 1
            """)
    Optional<DeviceCommand> lastWithStatus(String assetId, DeviceCommandStatus status);
}
