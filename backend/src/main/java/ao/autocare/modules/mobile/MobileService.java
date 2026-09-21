package ao.autocare.modules.mobile;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.RouteAssignment;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.org.PdfRenderer;
import java.time.Instant;
import java.time.LocalDate;
import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverAssignment;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.WorkOrderAttachmentKind;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.mobile.MobileDtos.AssetPick;
import ao.autocare.modules.mobile.MobileDtos.HomeView;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.workorder.WorkOrderAttachmentService;
import ao.autocare.modules.workorder.WorkOrderService;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CreateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderView;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverAssignmentRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * O que o telemóvel precisa e o resto do sistema não tem: um ecrã inicial
 * pequeno (a minha viatura, as minhas ordens) e «comunicar avaria» num só
 * passo — viatura, o que se passa, fotografia — que abre uma ordem corretiva
 * e avisa quem gere. O motorista não precisa de saber o que é uma ordem.
 */
@Service
public class MobileService {

    /** Estados em que uma ordem ainda tem trabalho pela frente. */
    private static final List<WorkOrderStatus> ABERTAS = List.of(
            WorkOrderStatus.OPEN, WorkOrderStatus.PLANNED, WorkOrderStatus.DIAGNOSIS, WorkOrderStatus.QUOTING,
            WorkOrderStatus.AWAITING_APPROVAL, WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.TESTING);

    private final AssetRepository assets;
    private final DriverRepository drivers;
    private final DriverAssignmentRepository assignments;
    private final WorkOrderRepository workOrders;
    private final UserRepository users;
    private final WorkOrderService workOrderService;
    private final WorkOrderAttachmentService attachments;
    private final NotificationService notifications;
    private final ao.autocare.modules.meter.MeterService meters;
    private final ao.autocare.repo.AssetMeterRepository meterRepo;
    private final ao.autocare.repo.AssetPlanTaskRepository planTasks;
    private final ao.autocare.repo.ChecklistExecutionRepository executions;
    private final ao.autocare.repo.RouteAssignmentRepository routeAssignments;
    private final ao.autocare.repo.RouteRepository routes;

    private static final java.time.ZoneId FUSO = java.time.ZoneId.of("Africa/Luanda");

    public MobileService(AssetRepository assets, DriverRepository drivers,
            DriverAssignmentRepository assignments, WorkOrderRepository workOrders, UserRepository users,
            WorkOrderService workOrderService, WorkOrderAttachmentService attachments,
            NotificationService notifications, ao.autocare.modules.meter.MeterService meters,
            ao.autocare.repo.AssetMeterRepository meterRepo,
            ao.autocare.repo.AssetPlanTaskRepository planTasks,
            ao.autocare.repo.ChecklistExecutionRepository executions,
            ao.autocare.repo.RouteAssignmentRepository routeAssignments,
            ao.autocare.repo.RouteRepository routes) {
        this.assets = assets;
        this.drivers = drivers;
        this.assignments = assignments;
        this.workOrders = workOrders;
        this.users = users;
        this.workOrderService = workOrderService;
        this.attachments = attachments;
        this.notifications = notifications;
        this.meters = meters;
        this.meterRepo = meterRepo;
        this.planTasks = planTasks;
        this.executions = executions;
        this.routeAssignments = routeAssignments;
        this.routes = routes;
    }

    @Transactional(readOnly = true)
    public HomeView home(String orgId, String userId) {
        return homeCompleto(orgId, userId);
    }

    private HomeView homeCompleto(String orgId, String userId) {
        // As viaturas atribuídas a esta pessoa (quando é motorista) vêm primeiro.
        Set<String> minhas = new HashSet<>();
        for (Driver d : drivers.findByOrganizationIdOrderByNameAsc(orgId)) {
            if (d.getUser() != null && userId.equals(d.getUser().getId())) {
                for (DriverAssignment a : assignments.openForDriver(d.getId())) {
                    minhas.add(a.getAsset().getId());
                }
            }
        }
        List<AssetPick> lista = new ArrayList<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            if (a.isArchived() || a.getStatus() == AssetStatus.RETIRED) {
                continue;
            }
            lista.add(new AssetPick(a.getId(), a.getTag(), a.getName(), a.getPlate(),
                    a.getStatus().name(), minhas.contains(a.getId())));
        }
        lista.sort(Comparator.comparing((AssetPick p) -> !p.mine()).thenComparing(AssetPick::tag,
                String.CASE_INSENSITIVE_ORDER));

        long abertas = workOrders.countByOrganizationIdAndAssignedToIdAndStatusIn(orgId, userId, ABERTAS);
        User u = users.findById(userId).orElse(null);

        List<MobileDtos.MinhaViatura> detalhe = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        for (String id : minhas) {
            assets.findByIdAndOrganizationId(id, orgId)
                    .filter(a -> !a.isArchived())
                    .ifPresent(a -> detalhe.add(detalhar(a, avisos)));
        }
        detalhe.sort(Comparator.comparing(MobileDtos.MinhaViatura::tag, String.CASE_INSENSITIVE_ORDER));

        return new HomeView(u != null ? u.getName() : "", lista, abertas, !minhas.isEmpty(),
                detalhe, rotaDeHoje(orgId, userId, minhas), avisos);
    }

    /**
     * Comunicar uma avaria: abre uma ordem corretiva «Alta» com a descrição e
     * a fotografia, e avisa os gestores (também no telemóvel, que é grave).
     */
    @Transactional
    public WorkOrderView reportBreakdown(String orgId, String userId, String assetId, String title,
            String description, BigDecimal meterValue, boolean stopped, MultipartFile photo) {
        return reportOccurrence(orgId, userId, assetId, title, description, meterValue, stopped,
                "AVARIA", null, null,
                photo == null ? java.util.List.of() : java.util.List.of(photo));
    }

    /**
     * Uma ocorrência comunicada do telemóvel: avaria, acidente ou o que for.
     *
     * <p>O que muda em relação a uma ordem aberta no escritório é tudo o que
     * vem agarrado: <b>as fotografias tiradas ali</b>, e <b>o sítio onde
     * aconteceu</b>. Um acidente descrito por palavras discute-se; fotografado
     * e localizado, resolve-se — com a seguradora, com o cliente e com o
     * motorista.
     */
    @org.springframework.transaction.annotation.Transactional
    public WorkOrderView reportOccurrence(String orgId, String userId, String assetId, String title,
            String description, BigDecimal meterValue, boolean stopped, String kind,
            BigDecimal latitude, BigDecimal longitude, java.util.List<MultipartFile> photos) {
        if (assetId == null || assetId.isBlank()) {
            throw ApiException.badRequest("Indique a viatura.");
        }
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("Diga em poucas palavras o que se passa.");
        }
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Viatura não encontrada."));
        User quem = users.findById(userId).orElseThrow(() -> ApiException.notFound("Utilizador não encontrado."));

        String tipo = kind == null || kind.isBlank() ? "AVARIA" : kind.trim().toUpperCase();
        String prefixo = switch (tipo) {
            case "ACIDENTE" -> "Acidente: ";
            case "PNEU" -> "Pneu: ";
            case "COMBUSTIVEL" -> "Combustível: ";
            default -> "";
        };
        String titulo = prefixo + title.trim();
        titulo = titulo.length() > 200 ? titulo.substring(0, 200) : titulo;
        StringBuilder corpo = new StringBuilder("Comunicado do telemóvel por " + quem.getName() + ".");
        if (description != null && !description.isBlank()) {
            corpo.append("\n\n").append(description.trim());
        }
        if (latitude != null && longitude != null) {
            // O sítio exacto vale mais do que qualquer descrição: é por aqui
            // que o gestor manda o reboque e que a seguradora confirma.
            corpo.append("\n\nLocal: ").append(latitude).append(", ").append(longitude)
                    .append("\nhttps://www.google.com/maps?q=").append(latitude).append(',').append(longitude);
        }
        String detalhe = corpo.toString();
        CreateWorkOrderRequest req = new CreateWorkOrderRequest(
                a.getId(), WorkOrderType.CORRECTIVE, titulo, detalhe,
                stopped ? WorkOrderPriority.URGENT : WorkOrderPriority.HIGH,
                null, null, null, null,
                new FailureInput(titulo, null, null, stopped),
                null, null, null, null, null, null, null, null, null, null, null);
        WorkOrderView ordem = workOrderService.create(orgId, userId, req);

        if (photos != null) {
            int n = 0;
            for (MultipartFile f : photos) {
                if (f == null || f.isEmpty()) {
                    continue;
                }
                n++;
                attachments.upload(orgId, userId, ordem.id(), f, WorkOrderAttachmentKind.BEFORE,
                        "Fotografia " + n + " tirada no telemóvel" + (
                                "ACIDENTE".equals(tipo) ? " (acidente)" : ""));
            }
        }
        if (meterValue != null) {
            // A leitura do painel no momento da avaria é uma leitura como outra qualquer.
            meters.recordFromWorkOrder(a, meterValue, java.time.Instant.now(), userId, ordem.number());
        }

        notifications.notifyManagers(NotificationService.Draft.of(
                orgId, AlertCategory.WORK_ORDER,
                stopped ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                (stopped ? "Viatura parada: " : switch (tipo) {
                    case "ACIDENTE" -> "Acidente comunicado: ";
                    case "PNEU" -> "Problema de pneu: ";
                    case "COMBUSTIVEL" -> "Problema de combustível: ";
                    default -> "Avaria comunicada: ";
                }) + a.getTag(),
                quem.getName() + " comunicou pelo telemóvel: " + titulo
                        + (description != null && !description.isBlank() ? " — " + description.trim() : "")
                        + ". Ordem " + ordem.number() + ".",
                "breakdown_report", ordem.id(), "/ordens/" + ordem.id()).forAsset(a));
        return workOrderService.get(orgId, ordem.id());
    }

    // ==== O que o motorista precisa de saber da viatura dele ================

    /**
     * A viatura com o contador, a próxima manutenção e a inspeção de hoje.
     *
     * <p>É aqui que a aplicação deixa de ser um formulário e passa a ser útil:
     * o motorista abre e vê, sem carregar em nada, se já fez a inspeção hoje e
     * quanto falta para a viatura ir à oficina.
     */
    private MobileDtos.MinhaViatura detalhar(Asset a, List<String> avisos) {
        AssetMeter principal = meterRepo.findByAssetId(a.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst().orElse(null);
        String unidade = principal != null
                ? (principal.getKind() == MeterKind.HOURMETER ? "h" : "km") : null;
        String contador = principal != null
                ? PdfRenderer.numero(principal.getCurrentValue(), 0) + " " + unidade : null;

        AssetPlanTask proxima = planTasks.findForAsset(a.getOrganization().getId(), a.getId()).stream()
                .filter(t -> t.getStatus() != null)
                .min(java.util.Comparator.comparing(t -> ordem(t.getStatus().name())))
                .orElse(null);
        String titulo = null;
        String falta = null;
        String estado = null;
        if (proxima != null) {
            titulo = proxima.getTitle();
            estado = proxima.getStatus().name();
            if (proxima.getRemainingMeter() != null) {
                boolean horas = proxima.getNextDueMeterKind() == MeterKind.HOURMETER;
                falta = "em " + PdfRenderer.numero(proxima.getRemainingMeter().abs(), 0)
                        + (horas ? " h" : " km");
            } else if (proxima.getRemainingDays() != null) {
                falta = "em " + proxima.getRemainingDays() + " dia(s)";
            }
            if ("OVERDUE".equals(estado)) {
                avisos.add(a.getTag() + ": manutenção vencida — " + titulo);
            }
        }

        Instant ultima = null;
        boolean hoje = false;
        var execucoes = executions.findByAssetIdOrderByPerformedAtDesc(a.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (!execucoes.isEmpty()) {
            ultima = execucoes.getContent().get(0).getPerformedAt();
            hoje = ultima != null && ultima.atZone(FUSO).toLocalDate().equals(LocalDate.now(FUSO));
        }
        if (!hoje) {
            avisos.add(a.getTag() + ": inspeção diária por fazer.");
        }
        if (a.getStatus() == AssetStatus.MAINTENANCE) {
            avisos.add(a.getTag() + ": está em manutenção — confirme com o gestor antes de sair.");
        }

        return new MobileDtos.MinhaViatura(a.getId(), a.getTag(), a.getName(), a.getPlate(),
                a.getStatus().name(), contador, unidade,
                principal != null ? principal.getCurrentValue() : null,
                titulo, falta, estado, hoje, ultima, a.getFuelLevelLiters());
    }

    /** Vencida primeiro, a vencer depois, em dia por último. */
    private static int ordem(String estado) {
        return switch (estado) {
            case "OVERDUE" -> 0;
            case "DUE_SOON" -> 1;
            default -> 2;
        };
    }

    /** A rota que o gestor marcou para hoje, para uma das viaturas dele. */
    private MobileDtos.RotaHoje rotaDeHoje(String orgId, String userId, java.util.Set<String> minhas) {
        LocalDate hoje = LocalDate.now(FUSO);
        for (RouteAssignment r : routeAssignments.forOrganization(orgId)) {
            boolean minhaViatura = r.getAsset() != null && minhas.contains(r.getAsset().getId());
            boolean euConduzo = r.getDriver() != null && r.getDriver().getUser() != null
                    && userId.equals(r.getDriver().getUser().getId());
            if (!minhaViatura && !euConduzo) {
                continue;
            }
            if (r.getPlannedFor() != null && !r.getPlannedFor().equals(hoje)) {
                continue;
            }
            var rota = r.getRoute();
            return new MobileDtos.RotaHoje(r.getId(), rota.getId(), rota.getName(), rota.getCode(),
                    r.getAsset() != null ? r.getAsset().getId() : null,
                    r.getAsset() != null ? r.getAsset().getTag() : null,
                    rota.getExpectedDistanceKm(), rota.getExpectedDurationMinutes(),
                    rota.getPathGeojson() != null && !rota.getPathGeojson().isBlank());
        }
        return null;
    }

    /**
     * A rota de hoje deste motorista, com o traçado para o mapa.
     *
     * <p>Devolve só o que é dele: um motorista não vê as rotas dos outros. Se
     * não houver nada marcado para hoje, devolve nada — e a aplicação diz isso
     * por palavras, em vez de mostrar um mapa vazio.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public MobileDtos.RotaDetalhe rotaDetalhe(String orgId, String userId) {
        java.util.Set<String> minhas = new HashSet<>();
        for (Driver d : drivers.findByOrganizationIdOrderByNameAsc(orgId)) {
            if (d.getUser() != null && userId.equals(d.getUser().getId())) {
                for (DriverAssignment a : assignments.openForDriver(d.getId())) {
                    minhas.add(a.getAsset().getId());
                }
            }
        }
        MobileDtos.RotaHoje hoje = rotaDeHoje(orgId, userId, minhas);
        if (hoje == null) {
            return null;
        }
        return routes.findByIdAndOrganizationId(hoje.routeId(), orgId).map(r -> {
            java.util.List<MobileDtos.PontoRota> pontos = new ArrayList<>();
            int i = 0;
            int total = r.getWaypoints().size();
            for (var w : r.getWaypoints()) {
                String tipo = i == 0 ? "origem" : (i == total - 1 ? "destino" : "passagem");
                pontos.add(new MobileDtos.PontoRota(w.getLabel(), w.getLatitude(), w.getLongitude(), tipo));
                i++;
            }
            // Quando a rota não tem rótulos escritos, servem os nomes do primeiro
            // e do último ponto: o motorista quer saber de onde a onde vai.
            String origem = r.getOriginLabel() != null && !r.getOriginLabel().isBlank()
                    ? r.getOriginLabel()
                    : (pontos.isEmpty() ? null : pontos.get(0).label());
            String destino = r.getDestinationLabel() != null && !r.getDestinationLabel().isBlank()
                    ? r.getDestinationLabel()
                    : (pontos.isEmpty() ? null : pontos.get(pontos.size() - 1).label());
            return new MobileDtos.RotaDetalhe(r.getId(), r.getName(), r.getCode(),
                    origem, destino,
                    r.getExpectedDistanceKm(), r.getExpectedDurationMinutes(),
                    r.getCorridorMeters(), r.getPathGeojson(), pontos,
                    hoje.assetId(), hoje.assetTag(), r.getNotes());
        }).orElse(null);
    }
}
