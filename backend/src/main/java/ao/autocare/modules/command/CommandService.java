package ao.autocare.modules.command;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.DeviceCommand;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.DeviceCommandKind;
import ao.autocare.domain.enums.Enums.CommandConfirmationSource;
import ao.autocare.domain.enums.Enums.DeviceCommandStatus;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.command.dto.CommandDtos.CommandView;
import ao.autocare.modules.command.dto.CommandDtos.DeviceSyncView;
import ao.autocare.modules.command.dto.CommandDtos.LockStatusView;
import ao.autocare.modules.command.dto.CommandDtos.ProviderHealthView;
import ao.autocare.modules.command.dto.CommandDtos.RequestCommandRequest;
import ao.autocare.modules.command.dto.CommandDtos.SafetyView;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DeviceCommandRepository;
import ao.autocare.repo.GpsDeviceRepository;
import ao.autocare.repo.GpsPositionRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueio remoto do motor.
 *
 * <p>Esta classe existe sobretudo para <b>não</b> executar comandos. As regras
 * que a governam vêm todas do mesmo princípio: um corte de motor no momento
 * errado mata pessoas, e nenhuma conveniência de gestão de frota justifica esse
 * risco.
 *
 * <ul>
 *   <li>Pedir e executar são coisas separadas. O comando entra em fila e só sai
 *       quando a viatura estiver <b>comprovadamente parada</b> — várias leituras
 *       consecutivas a zero, nunca uma amostra isolada, porque um GPS a oscilar
 *       reporta 0 km/h a meio da estrada.</li>
 *   <li>Precisa de aprovação num segundo passo. Se a empresa tiver mais do que
 *       um dono, tem de ser outra pessoa a aprovar.</li>
 *   <li>Caduca. Um corte pedido de manhã não pode disparar à noite.</li>
 *   <li>Desbloquear é sempre permitido e imediato: não conseguir desbloquear é,
 *       por si só, um perigo.</li>
 * </ul>
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    /** Leituras consecutivas paradas exigidas antes de cortar. */
    private static final int REQUIRED_STOPPED_READINGS = 3;

    /** Acima disto a viatura não é considerada parada. */
    private static final BigDecimal STOPPED_SPEED_KPH = new BigDecimal("1");

    /**
     * Idade máxima da última posição. Sem notícias recentes não se sabe onde a
     * viatura está nem o que está a fazer — e nesse estado não se corta nada.
     */
    private static final Duration POSITION_FRESHNESS = Duration.ofMinutes(10);

    /** Prazo por omissão para um pedido caducar sem ser executado. */
    private static final Duration DEFAULT_EXPIRY = Duration.ofHours(4);

    /**
     * Quanto tempo se espera por prova do aparelho antes de desistir.
     *
     * <p>Sem este prazo um comando ficava em "enviado" para sempre — e há
     * protocolos que nunca reportam nada. O efeito era duplo e mau: a sondagem
     * crescia sem fim, e o comando preso impedia qualquer comando novo para a
     * mesma viatura. Desistir não é fingir: o estado passa a dizer, por
     * palavras, que <b>não se sabe</b> se a viatura ficou bloqueada.
     */
    private static final Duration CONFIRMATION_DEADLINE = Duration.ofMinutes(30);

    /** Estados em que um comando ainda está por resolver. */
    private static final List<DeviceCommandStatus> OPEN_STATUSES = List.of(
            DeviceCommandStatus.PENDING_APPROVAL,
            DeviceCommandStatus.QUEUED,
            DeviceCommandStatus.SENT);

    private final DeviceCommandRepository commands;
    private final AssetRepository assets;
    private final GpsDeviceRepository devices;
    private final GpsPositionRepository positions;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final CommandProvider provider;
    private final NotificationService notifications;
    private final AuditService audit;

    public CommandService(
            DeviceCommandRepository commands,
            AssetRepository assets,
            GpsDeviceRepository devices,
            GpsPositionRepository positions,
            MembershipRepository memberships,
            UserRepository users,
            CommandProvider provider,
            NotificationService notifications,
            AuditService audit) {
        this.commands = commands;
        this.assets = assets;
        this.devices = devices;
        this.positions = positions;
        this.memberships = memberships;
        this.users = users;
        this.provider = provider;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ==== Pedido ========================================================
    @Transactional
    public CommandView request(
            String orgId, String userId, String assetId,
            DeviceCommandKind kind, RequestCommandRequest req) {

        Asset asset = requireAsset(orgId, assetId);
        GpsDevice device = devices.findFirstByAssetId(assetId)
                .orElseThrow(() -> ApiException.conflict(
                        "Este ativo não tem aparelho de GPS instalado; não há por onde "
                                + "enviar o comando."));

        if (req.reason() == null || req.reason().isBlank()) {
            throw ApiException.badRequest("Indique o motivo. Fica registado.");
        }
        if (!Boolean.TRUE.equals(req.acknowledged())) {
            throw ApiException.badRequest(
                    "Confirme que compreende o efeito do comando antes de o pedir.");
        }
        // Um aparelho que não sabe imobilizar não vai aprender no caminho. Saber
        // isto no pedido e não na entrega poupa minutos — e num furto é o que há.
        if (kind == DeviceCommandKind.ENGINE_STOP
                && device.getCommandsSyncedAt() != null && !device.supportsImmobiliser()) {
            throw ApiException.conflict("O aparelho " + device.getExternalId()
                    + (device.getProtocol() != null ? " (protocolo " + device.getProtocol() + ")" : "")
                    + " não suporta imobilização, segundo o próprio fornecedor. Este bloqueio "
                    + "não iria funcionar. Se acredita que o aparelho foi substituído ou "
                    + "reconfigurado, sincronize-o outra vez em Definições → Servidor de comandos.");
        }

        List<DeviceCommand> open = commands.findByAssetIdAndStatusIn(assetId, OPEN_STATUSES);

        // DESBLOQUEAR NUNCA É TRAVADO. Travar qualquer comando enquanto houvesse
        // outro por resolver evitava um bloqueio e um desbloqueio em fila ao
        // mesmo tempo — mas criou coisa pior: um bloqueio preso em "enviado",
        // que nunca caduca e que nenhum protocolo silencioso vai confirmar,
        // deixava a viatura SEM FORMA DE SER DESBLOQUEADA. É o perigo que este
        // módulo existe para evitar. O comando aberto é substituído, com registo.
        if (kind == DeviceCommandKind.ENGINE_RESUME) {
            supersede(open, userId);
        } else if (!open.isEmpty()) {
            DeviceCommand existing = open.get(0);
            throw ApiException.conflict("Já existe um comando por resolver para este ativo ("
                    + existing.getKind() + ", " + CommandView.label(existing.getStatus())
                    + "). Anule-o antes de pedir outro — ou peça o desbloqueio, "
                    + "que tem sempre precedência.");
        }

        GpsPosition last = positions.findFirstByAssetIdOrderByRecordedAtDesc(assetId).orElse(null);

        DeviceCommand c = new DeviceCommand();
        c.setOrganization(asset.getOrganization());
        c.setAsset(asset);
        c.setDevice(device);
        c.setKind(kind);
        c.setReason(req.reason().trim());
        c.setReasonCategory(req.reasonCategory());
        c.setPreviousLockState(currentlyLocked(assetId) ? "LOCKED" : "FREE");
        c.setRequestedBy(users.getReferenceById(userId));
        c.setRequestedAt(Instant.now());
        c.setExpiresAt(Instant.now().plus(DEFAULT_EXPIRY));
        if (last != null) {
            c.setRequestLatitude(last.getLatitude());
            c.setRequestLongitude(last.getLongitude());
            c.setRequestSpeedKph(last.getSpeedKph());
        }
        // Desbloquear não precisa de aprovação nem de travas: não conseguir
        // desbloquear uma viatura é, por si só, um perigo.
        c.setStatus(kind == DeviceCommandKind.ENGINE_RESUME
                ? DeviceCommandStatus.QUEUED : DeviceCommandStatus.PENDING_APPROVAL);
        commands.save(c);

        audit.record(orgId, userId, "command.request", "Asset", assetId,
                kind + " · " + asset.getTag() + " · motivo: " + c.getReason());

        if (kind == DeviceCommandKind.ENGINE_RESUME) {
            deliver(c);
        } else {
            notifyManagers(c, "Pedido de bloqueio — " + asset.getTag(),
                    c.getReason() + " · pedido por "
                            + c.getRequestedBy().getName() + ". Falta aprovação.");
        }
        return view(c);
    }

    // ==== Aprovação =====================================================
    @Transactional
    public CommandView approve(String orgId, String userId, String commandId) {
        DeviceCommand c = require(orgId, commandId);
        if (c.getStatus() != DeviceCommandStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Este pedido já não está à espera de aprovação.");
        }
        if (c.isExpired(Instant.now())) {
            c.setStatus(DeviceCommandStatus.EXPIRED);
            throw ApiException.conflict("Este pedido caducou. Faça um novo.");
        }
        requireSecondPerson(orgId, c, userId);

        c.setApprovedBy(users.getReferenceById(userId));
        c.setApprovedAt(Instant.now());
        c.setStatus(DeviceCommandStatus.QUEUED);

        audit.record(orgId, userId, "command.approve", "DeviceCommand", c.getId(),
                c.getKind() + " · " + c.getAsset().getTag());

        // Tenta já: se a viatura estiver parada, sai agora; senão fica em fila.
        deliver(c);
        return view(c);
    }

    /**
     * Exige que aprove alguém diferente de quem pediu, <b>quando a empresa tem
     * mais do que um dono ativo</b>. Numa empresa com um único dono, obrigar a
     * duas pessoas tornaria a função inutilizável; nesse caso a aprovação é um
     * segundo ato deliberado da mesma pessoa, registado à parte.
     */
    private void requireSecondPerson(String orgId, DeviceCommand c, String userId) {
        boolean samePerson = c.getRequestedBy() != null
                && c.getRequestedBy().getId().equals(userId);
        if (!samePerson) {
            return;
        }
        long owners = memberships.countByOrganizationIdAndRoleAndSuspendedAtIsNull(
                orgId, MembershipRole.OWNER);
        if (owners > 1) {
            throw ApiException.forbidden(
                    "Um bloqueio tem de ser aprovado por outra pessoa. "
                            + "Peça a outro dono da empresa que aprove.");
        }
    }

    /**
     * Marca os comandos abertos como substituídos por um desbloqueio.
     *
     * <p>Há aqui uma honestidade desconfortável a manter. Um comando que já saiu
     * para o fornecedor <b>não pode ser recolhido pelo AutoCare</b>: se o
     * aparelho estava offline, o Traccar guardou-o e vai entregá-lo quando ele
     * ligar, possivelmente depois do desbloqueio. Fingir que substituir apaga o
     * comando anterior seria mentir sobre o estado de uma viatura. Por isso o
     * aviso diz exatamente isso a quem o pediu.
     */
    private void supersede(List<DeviceCommand> open, String userId) {
        for (DeviceCommand c : open) {
            boolean jaSaiu = c.getStatus() == DeviceCommandStatus.SENT;
            c.setStatus(DeviceCommandStatus.SUPERSEDED);
            c.setFailureReason(jaSaiu
                    ? "Substituído por um pedido de desbloqueio. O comando já tinha saído "
                            + "para o fornecedor e o AutoCare não o consegue recolher: se o "
                            + "aparelho estava offline, pode ainda vir a ser executado."
                    : "Substituído por um pedido de desbloqueio antes de chegar a ser enviado.");

            audit.record(c.getOrganization().getId(), userId,
                    "command.supersede", "DeviceCommand", c.getId(),
                    c.getKind() + " · " + c.getAsset().getTag()
                            + " · substituído por um desbloqueio"
                            + (jaSaiu ? " depois de já ter saído para o fornecedor" : ""));

            if (jaSaiu) {
                notifyManagers(c, "Bloqueio substituído — " + c.getAsset().getTag(),
                        "Foi pedido o desbloqueio desta viatura enquanto um bloqueio estava "
                                + "por confirmar. O bloqueio já tinha sido entregue ao "
                                + provider.name() + " e não pode ser recolhido daqui. Se o "
                                + "aparelho estava offline, confirme no local que a viatura "
                                + "arranca.");
            }
        }
    }

    @Transactional
    public CommandView cancel(String orgId, String userId, String commandId) {
        DeviceCommand c = require(orgId, commandId);
        if (!c.isPending()) {
            throw ApiException.conflict("Este comando já não pode ser anulado.");
        }
        c.setStatus(DeviceCommandStatus.CANCELLED);
        c.setCancelledBy(users.getReferenceById(userId));
        c.setCancelledAt(Instant.now());
        audit.record(orgId, userId, "command.cancel", "DeviceCommand", c.getId(),
                c.getKind() + " · " + c.getAsset().getTag());
        return view(c);
    }

    // ==== Entrega =======================================================
    /**
     * Envia o comando se — e só se — as condições de segurança se verificarem.
     * Caso contrário deixa-o em fila; o agendador volta a tentar.
     */
    @Transactional
    public void deliver(DeviceCommand c) {
        if (c.getStatus() != DeviceCommandStatus.QUEUED) {
            return;
        }
        if (c.isExpired(Instant.now())) {
            expire(c);
            return;
        }
        if (c.getKind() == DeviceCommandKind.ENGINE_STOP) {
            SafetyView safety = safety(c.getAsset().getId());
            if (!safety.safe()) {
                log.debug("Bloqueio de {} em espera: {}",
                        c.getAsset().getTag(), safety.reason());
                return;
            }
        }

        GpsPosition last = positions
                .findFirstByAssetIdOrderByRecordedAtDesc(c.getAsset().getId()).orElse(null);
        if (last != null) {
            c.setSentLatitude(last.getLatitude());
            c.setSentLongitude(last.getLongitude());
            c.setSentSpeedKph(last.getSpeedKph());
        }
        c.setAttempts(c.getAttempts() + 1);

        CommandProvider.Dispatch dispatch = provider.dispatch(c);
        if (!dispatch.accepted()) {
            c.setStatus(DeviceCommandStatus.FAILED);
            c.setFailureReason(dispatch.failureReason());
            notifyManagers(c, "Comando falhou — " + c.getAsset().getTag(),
                    dispatch.failureReason());
            return;
        }
        c.setStatus(DeviceCommandStatus.SENT);
        c.setSentAt(Instant.now());
        c.setProviderCommandId(dispatch.providerCommandId());
        c.setProviderQueued(dispatch.queued());

        notifyManagers(c,
                (c.getKind() == DeviceCommandKind.ENGINE_STOP ? "Bloqueio enviado — "
                        : "Desbloqueio enviado — ") + c.getAsset().getTag(),
                dispatch.queued()
                        ? "O aparelho está offline: o comando ficou em fila no "
                                + provider.name() + " e será executado quando ele ligar. "
                                + "A viatura NÃO está bloqueada."
                        : "Entregue a " + provider.name() + ". Aguarda confirmação do aparelho.");
    }

    /**
     * Confirmação declarada por uma pessoa, sem prova do aparelho.
     *
     * <p>Existe porque há protocolos que nunca reportam nada: alguém foi lá,
     * viu, e regista. Mas fica marcada como {@code MANUAL} e o ecrã mostra-o —
     * uma imobilização "confirmada" por afirmação não vale como garantia, e
     * confundir as duas coisas é o que faz alguém contar com um bloqueio que
     * não existe.
     */
    @Transactional
    public CommandView confirmManually(String orgId, String userId, String commandId) {
        DeviceCommand c = require(orgId, commandId);
        if (c.getStatus() != DeviceCommandStatus.SENT) {
            throw ApiException.conflict("Só um comando enviado pode ser confirmado.");
        }
        markConfirmed(c, CommandConfirmationSource.MANUAL);
        audit.record(orgId, userId, "command.confirm_manual", "DeviceCommand", c.getId(),
                c.getKind() + " · " + c.getAsset().getTag()
                        + " · confirmado por declaração, sem prova do aparelho");
        return view(c);
    }

    /**
     * Vai perguntar ao fornecedor se há prova de execução dos comandos enviados.
     *
     * <p>É isto que transforma "enviado" em "confirmado" sem depender de alguém
     * carregar num botão. Quando não há prova — e há protocolos que nunca a
     * dão — o comando fica em "enviado, por confirmar", que é a verdade.
     *
     * @return quantos passaram a confirmado
     */
    @Transactional
    public int pollConfirmations() {
        Instant now = Instant.now();

        // Primeiro desistir do que já passou do prazo. Se ficassem na lista,
        // seriam sondados para sempre — e continuariam a impedir comandos novos.
        for (DeviceCommand c : commands.unconfirmedBefore(now.minus(CONFIRMATION_DEADLINE))) {
            giveUpOnConfirmation(c);
        }

        int confirmed = 0;
        for (DeviceCommand c : commands.awaitingConfirmation(now.minus(CONFIRMATION_DEADLINE))) {
            try {
                c.setLastCheckedAt(Instant.now());
                CommandProvider.Evidence evidence = provider.confirmationFor(c);
                if (!evidence.conclusive()) {
                    continue;
                }
                markConfirmed(c, evidence.source());
                confirmed++;

                notifyManagers(c,
                        (c.getKind() == DeviceCommandKind.ENGINE_STOP
                                ? "Bloqueio confirmado — " : "Desbloqueio confirmado — ")
                                + c.getAsset().getTag(),
                        evidence.detail());

            } catch (Exception e) {
                log.warn("Falha ao confirmar o comando {}: {}", c.getId(), e.toString());
            }
        }
        return confirmed;
    }

    /**
     * Desiste de esperar por prova do aparelho.
     *
     * <p>O estado que fica — "enviado, o aparelho nunca confirmou" — é
     * deliberadamente desconfortável de ler. É a descrição exata da situação:
     * o comando saiu, e <b>ninguém sabe</b> o que aconteceu do outro lado.
     * Chamar-lhe "confirmado" ou "falhado" seria escolher uma resposta que os
     * dados não dão, e é dessa escolha que nascem os bloqueios em que alguém
     * confia sem ter razão para isso.
     */
    private void giveUpOnConfirmation(DeviceCommand c) {
        boolean bloqueio = c.getKind() == DeviceCommandKind.ENGINE_STOP;
        c.setStatus(DeviceCommandStatus.UNCONFIRMED);
        c.setLastCheckedAt(Instant.now());
        c.setFailureReason("O aparelho não deu qualquer prova de execução em "
                + CONFIRMATION_DEADLINE.toMinutes() + " minutos.");
        // O estado de bloqueio NÃO é atualizado: não se sabe qual é.

        notifyManagers(c,
                (bloqueio ? "Bloqueio por confirmar — " : "Desbloqueio por confirmar — ")
                        + c.getAsset().getTag(),
                "O comando foi entregue a " + provider.name() + " mas o aparelho nunca "
                        + "confirmou a execução em " + CONFIRMATION_DEADLINE.toMinutes()
                        + " minutos. NÃO se sabe se a viatura está "
                        + (bloqueio ? "bloqueada" : "a poder arrancar")
                        + ". Confirme no local antes de contar com isso.");
    }

    private void markConfirmed(DeviceCommand c, CommandConfirmationSource source) {
        c.setStatus(DeviceCommandStatus.CONFIRMED);
        c.setConfirmedAt(Instant.now());
        c.setConfirmationSource(source);
        c.setResultingLockState(
                c.getKind() == DeviceCommandKind.ENGINE_STOP ? "LOCKED" : "FREE");
    }

    /** Estado de bloqueio atual, a partir do último comando confirmado. */
    private boolean currentlyLocked(String assetId) {
        return commands.lastWithStatus(assetId, DeviceCommandStatus.CONFIRMED)
                .map(c -> c.getKind() == DeviceCommandKind.ENGINE_STOP)
                .orElse(false);
    }

    // ==== Segurança =====================================================
    /**
     * Diz se é seguro cortar o motor deste ativo agora, e porquê — a razão é
     * mostrada a quem pede, para a espera não parecer uma avaria.
     */
    @Transactional(readOnly = true)
    public SafetyView safety(String assetId) {
        List<GpsPosition> recent = positions
                .findByAssetIdOrderByRecordedAtDesc(assetId,
                        PageRequest.of(0, REQUIRED_STOPPED_READINGS))
                .getContent();

        if (recent.isEmpty()) {
            return new SafetyView(false, 0,
                    "Este ativo nunca comunicou posição. Sem saber onde está nem o "
                            + "que está a fazer, o bloqueio não é enviado.");
        }
        GpsPosition last = recent.get(0);
        Duration age = Duration.between(last.getRecordedAt(), Instant.now());
        if (age.compareTo(POSITION_FRESHNESS) > 0) {
            return new SafetyView(false, 0,
                    "A última posição tem " + age.toMinutes() + " minutos. "
                            + "Sem notícias recentes o bloqueio fica em espera.");
        }
        // Contar primeiro, decidir depois. Contar as leituras existentes em vez
        // das leituras PARADAS dava um número que não correspondia ao nome do
        // campo, e a razão mostrada escondia o essencial: se está em movimento.
        int stopped = 0;
        for (GpsPosition p : recent) {
            if (isMoving(p)) {
                break;
            }
            stopped++;
        }
        if (stopped >= REQUIRED_STOPPED_READINGS) {
            return new SafetyView(true, stopped,
                    "Parada nas últimas " + REQUIRED_STOPPED_READINGS + " leituras.");
        }
        if (isMoving(recent.get(0))) {
            return new SafetyView(false, stopped,
                    "A viatura está em movimento. O bloqueio fica em fila e só sai "
                            + "quando ela parar.");
        }
        return new SafetyView(false, stopped,
                "Parada há " + stopped + " leitura(s); são precisas "
                        + REQUIRED_STOPPED_READINGS + " seguidas antes de cortar.");
    }

    /** Em marcha: a velocidade manda; sem velocidade, vale o movimento detetado. */
    private boolean isMoving(GpsPosition p) {
        if (p.getSpeedKph() != null) {
            return p.getSpeedKph().compareTo(STOPPED_SPEED_KPH) > 0;
        }
        return Boolean.TRUE.equals(p.getMoving());
    }

    /** Estado de bloqueio de um ativo, para o ecrã. */
    @Transactional(readOnly = true)
    public LockStatusView lockStatus(String orgId, String assetId) {
        Asset asset = requireAsset(orgId, assetId);
        DeviceCommand lastConfirmed = commands
                .lastWithStatus(assetId, DeviceCommandStatus.CONFIRMED).orElse(null);
        boolean locked = lastConfirmed != null
                && lastConfirmed.getKind() == DeviceCommandKind.ENGINE_STOP;

        // Qualquer comando por resolver, de qualquer tipo: um desbloqueio em
        // curso interessa tanto a quem olha para o ecrã como um bloqueio.
        List<DeviceCommand> pending = commands.findByAssetIdAndStatusIn(assetId, OPEN_STATUSES);

        GpsDevice device = devices.findFirstByAssetId(assetId).orElse(null);
        Boolean immobiliser = device == null || device.getCommandsSyncedAt() == null
                ? null : device.supportsImmobiliser();

        return new LockStatusView(
                asset.getId(), asset.getTag(), locked,
                lastConfirmed != null ? lastConfirmed.getConfirmedAt() : null,
                lastConfirmed != null ? lastConfirmed.getConfirmationSource() : null,
                lastConfirmed != null
                        ? CommandView.confirmationLabel(lastConfirmed.getConfirmationSource())
                        : null,
                pending.isEmpty() ? null : view(pending.get(0)),
                provider.isConfigured(), provider.name(),
                device != null ? device.getExternalId() : null,
                device != null ? device.getProtocol() : null,
                immobiliser,
                device != null && device.getCommandsSyncedAt() != null
                        ? "Comandos verificados junto do fornecedor."
                        : "Comandos do aparelho ainda não verificados junto do fornecedor.",
                safety(assetId));
    }

    /** Estado da ligação ao fornecedor, sem enviar nada a nenhum aparelho. */
    @Transactional(readOnly = true)
    public ProviderHealthView providerHealth() {
        CommandProvider.ProviderHealth health = provider.health();
        return new ProviderHealthView(health.configured(), health.reachable(),
                health.name(), health.version(), health.failureReason());
    }

    /**
     * Pergunta ao fornecedor o protocolo do aparelho e que comandos ele aceita,
     * e guarda a resposta.
     *
     * <p>Sem isto o sistema enviava "engineStop" às cegas. Há protocolos que não
     * o suportam, e nesse caso o comando morre no fornecedor sem ninguém saber.
     */
    @Transactional
    public DeviceSyncView syncDevice(String orgId, String userId, String deviceId) {
        GpsDevice device = devices.findByIdAndOrganizationId(deviceId, orgId)
                .orElseThrow(() -> ApiException.notFound("Aparelho não encontrado."));

        if (!provider.isConfigured()) {
            throw ApiException.conflict(
                    "Não há fornecedor de comandos configurado; não há a quem perguntar.");
        }
        CommandProvider.DeviceInfo info = provider.describeDevice(device.getExternalId())
                .orElseThrow(() -> ApiException.notFound(
                        "O aparelho " + device.getExternalId() + " não existe no "
                                + provider.name() + ". Verifique o identificador único."));

        device.setProviderDeviceId(info.providerDeviceId());
        device.setProtocol(info.protocol());
        device.setSupportedCommands(writeCommands(info.supportedCommands()));
        device.setCommandsSyncedAt(Instant.now());

        audit.record(orgId, userId, "gps_device.sync", "GpsDevice", device.getId(),
                device.getExternalId() + " - protocolo " + info.protocol());

        return new DeviceSyncView(device.getId(), device.getExternalId(), info.protocol(),
                info.status(), info.online(), info.supportedCommands(),
                info.supportedCommands().contains("engineStop"), device.getCommandsSyncedAt());
    }

    private String writeCommands(List<String> commandsList) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(commandsList);
        } catch (Exception e) {
            return null;
        }
    }

    // ==== Manutenção da fila ============================================
    /** Chamado pelo agendador: tenta enviar o que está em fila e expira o resto. */
    @Transactional
    public int processQueue() {
        Instant now = Instant.now();
        int sent = 0;

        for (DeviceCommand c : commands.expired(
                List.of(DeviceCommandStatus.PENDING_APPROVAL, DeviceCommandStatus.QUEUED), now)) {
            expire(c);
        }
        for (DeviceCommand c : commands.queued(DeviceCommandStatus.QUEUED)) {
            DeviceCommandStatus before = c.getStatus();
            deliver(c);
            if (before != c.getStatus() && c.getStatus() == DeviceCommandStatus.SENT) {
                sent++;
            }
        }
        return sent;
    }

    private void expire(DeviceCommand c) {
        c.setStatus(DeviceCommandStatus.EXPIRED);
        c.setFailureReason("Caducou sem que houvesse condições de segurança para o enviar.");
        notifyManagers(c, "Pedido caducou — " + c.getAsset().getTag(),
                "O pedido de " + c.getKind() + " caducou sem ser executado.");
    }

    // ==== Consulta ======================================================
    @Transactional(readOnly = true)
    public PagedResponse<CommandView> list(String orgId, Pageable pageable) {
        return PagedResponse.of(
                commands.findByOrganizationIdOrderByRequestedAtDesc(orgId, pageable)
                        .map(this::view));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommandView> listForAsset(
            String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                commands.findByAssetIdOrderByRequestedAtDesc(assetId, pageable).map(this::view));
    }

    // ==== Auxiliares ====================================================
    private void notifyManagers(DeviceCommand c, String title, String body) {
        notifications.notifyManagers(NotificationService.Draft.of(
                        c.getOrganization().getId(),
                        AlertCategory.SYSTEM, AlertSeverity.CRITICAL,
                        title, body,
                        "device_command", c.getId() + ":" + c.getStatus(),
                        "/frota/comandos/" + c.getId())
                .forAsset(c.getAsset()));
    }

    private CommandView view(DeviceCommand c) {
        return CommandView.of(c);
    }

    private DeviceCommand require(String orgId, String id) {
        return commands.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Comando não encontrado."));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }
}
