package ao.autocare.modules.predictive;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.PredictiveProgram;
import ao.autocare.domain.PredictiveReading;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.PredictiveResult;
import ao.autocare.domain.enums.Enums.PredictiveTechnique;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ProgramView;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ReadingRecorded;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ReadingView;
import ao.autocare.modules.predictive.dto.PredictiveDtos.RecordReadingRequest;
import ao.autocare.modules.predictive.dto.PredictiveDtos.SaveProgramRequest;
import ao.autocare.modules.predictive.dto.PredictiveDtos.TechniqueView;
import ao.autocare.modules.predictive.dto.PredictiveDtos.UpdateProgramRequest;
import ao.autocare.modules.workorder.WorkOrderService;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CreateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderView;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PredictiveProgramRepository;
import ao.autocare.repo.PredictiveReadingRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.storage.FileUrls;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manutenção preditiva: programas de monitorização de condição por ativo e o
 * registo dos seus resultados.
 *
 * <p>A diferença para o plano preventivo é o que dispara a intervenção. O plano
 * conta horas; aqui olha-se para o estado real do equipamento — a vibração de um
 * rolamento, a temperatura de uma ligação elétrica, as partículas metálicas no
 * óleo. Uma medição não é uma ordem de trabalho: é informação. Por isso quem
 * mede também <em>classifica</em>, e só um resultado classificado como grave é
 * que gera acção.
 */
@Service
public class PredictiveService {

    /** As três técnicas do documento de referência, com a periodicidade de lá. */
    private static final List<PredictiveTechnique> STANDARD_SET = List.of(
            PredictiveTechnique.VIBRATION,
            PredictiveTechnique.THERMOGRAPHY,
            PredictiveTechnique.OIL_ANALYSIS);

    private final PredictiveProgramRepository programs;
    private final PredictiveReadingRepository readings;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final StoredFileRepository files;
    private final WorkOrderService workOrders;
    private final NotificationService notifications;
    private final FileUrls fileUrls;
    private final AuditService audit;

    public PredictiveService(
            PredictiveProgramRepository programs,
            PredictiveReadingRepository readings,
            AssetRepository assets,
            OrganizationRepository organizations,
            UserRepository users,
            StoredFileRepository files,
            WorkOrderService workOrders,
            NotificationService notifications,
            FileUrls fileUrls,
            AuditService audit) {
        this.programs = programs;
        this.readings = readings;
        this.assets = assets;
        this.organizations = organizations;
        this.users = users;
        this.files = files;
        this.workOrders = workOrders;
        this.notifications = notifications;
        this.fileUrls = fileUrls;
        this.audit = audit;
    }

    // ==== Técnicas ======================================================
    public List<TechniqueView> techniques() {
        return Arrays.stream(PredictiveTechnique.values())
                .map(t -> new TechniqueView(t, t.label(), t.defaultFrequencyMonths(),
                        ProgramView.frequencyLabel(t.defaultFrequencyMonths())))
                .toList();
    }

    // ==== Programas =====================================================
    @Transactional(readOnly = true)
    public List<ProgramView> listForAsset(String orgId, String assetId) {
        requireAsset(orgId, assetId);
        Instant now = Instant.now();
        return programs.findByAssetIdOrderByTechniqueAsc(assetId).stream()
                .map(p -> ProgramView.of(p, now, lastResult(p))).toList();
    }

    /** Programas da frota, opcionalmente só os que estão vencidos ou a chegar. */
    @Transactional(readOnly = true)
    public List<ProgramView> listForOrg(String orgId, String status) {
        Instant now = Instant.now();
        PlanTaskStatus wanted = parseStatus(status);
        List<ProgramView> out = new ArrayList<>();
        for (PredictiveProgram p : programs.findActive(orgId)) {
            if (wanted != null && p.statusAt(now) != wanted) {
                continue;
            }
            out.add(ProgramView.of(p, now, lastResult(p)));
        }
        return out;
    }

    @Transactional
    public ProgramView create(String orgId, String userId, String assetId, SaveProgramRequest req) {
        Asset asset = requireAsset(orgId, assetId);
        programs.findByAssetIdAndTechnique(assetId, req.technique()).ifPresent(existing -> {
            throw ApiException.conflict("Este ativo já tem um programa de "
                    + req.technique().label().toLowerCase() + ".");
        });

        PredictiveProgram p = new PredictiveProgram();
        p.setOrganization(organizations.getReferenceById(orgId));
        p.setAsset(asset);
        p.setTechnique(req.technique());
        p.setFrequencyMonths(req.frequencyMonths() != null
                ? req.frequencyMonths() : req.technique().defaultFrequencyMonths());
        apply(p, req);
        reschedule(p);
        programs.save(p);

        audit.record(orgId, userId, "predictive.create", "PredictiveProgram", p.getId(),
                asset.getTag() + " · " + p.getTechnique().label());
        return ProgramView.of(p, Instant.now(), null);
    }

    /**
     * Aplica ao ativo o conjunto do documento de referência: vibração mensal,
     * termografia trimestral e análise de óleo semestral. Os programas que já
     * existirem ficam como estão.
     */
    @Transactional
    public List<ProgramView> applyStandardSet(String orgId, String userId, String assetId) {
        Asset asset = requireAsset(orgId, assetId);

        // O conjunto certo não é o mesmo para tudo: num gerador o que interessa
        // é o ensaio de isolamento do alternador, e num camião o alinhamento —
        // medir vibração no painel elétrico de um gerador não diz nada a
        // ninguém. A família do ativo é que decide.
        var doCatalogo = ao.autocare.modules.plan.PlanCatalog.predictivePrograms(familiaDe(asset));
        if (doCatalogo.isEmpty()) {
            for (PredictiveTechnique technique : STANDARD_SET) {
                criarSeFaltar(orgId, asset, technique, technique.defaultFrequencyMonths(),
                        standardComponents(technique), standardGoal(technique));
            }
        } else {
            for (var req : doCatalogo) {
                criarSeFaltar(orgId, asset, req.technique(),
                        req.frequencyMonths() != null
                                ? req.frequencyMonths() : req.technique().defaultFrequencyMonths(),
                        req.components(), req.goal());
            }
        }
        audit.record(orgId, userId, "predictive.standard_set", "Asset", assetId, asset.getTag());
        return listForAsset(orgId, assetId);
    }

    @Transactional
    public ProgramView update(
            String orgId, String userId, String programId, UpdateProgramRequest update) {

        PredictiveProgram p = require(orgId, programId);
        SaveProgramRequest req = update.asSave(p.getTechnique());
        if (req.frequencyMonths() != null && req.frequencyMonths() != p.getFrequencyMonths()) {
            p.setFrequencyMonths(req.frequencyMonths());
            reschedule(p);
        }
        apply(p, req);
        audit.record(orgId, userId, "predictive.update", "PredictiveProgram", p.getId(),
                p.getAsset().getTag() + " · " + p.getTechnique().label());
        return ProgramView.of(p, Instant.now(), lastResult(p));
    }

    @Transactional
    public void delete(String orgId, String userId, String programId) {
        PredictiveProgram p = require(orgId, programId);
        String label = p.getAsset().getTag() + " · " + p.getTechnique().label();
        programs.delete(p);
        audit.record(orgId, userId, "predictive.delete", "PredictiveProgram", programId, label);
    }

    // ==== Medições ======================================================
    @Transactional
    public ReadingRecorded record(
            String orgId, String userId, String programId, RecordReadingRequest req) {

        PredictiveProgram p = require(orgId, programId);
        Instant performedAt = req.performedAt() != null ? req.performedAt() : Instant.now();
        if (performedAt.isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.badRequest("A data da medição está no futuro.");
        }

        PredictiveReading r = new PredictiveReading();
        r.setOrganization(p.getOrganization());
        r.setProgram(p);
        r.setAsset(p.getAsset());
        r.setPerformedAt(performedAt);
        r.setResult(req.result());
        r.setMeasurement(blankToNull(req.measurement()));
        r.setFindings(blankToNull(req.findings()));
        r.setRecommendation(blankToNull(req.recommendation()));
        r.setPerformedByLabel(blankToNull(req.performedByLabel()));
        r.setMeterValue(req.meterValue());
        if (userId != null) {
            r.setPerformedBy(users.getReferenceById(userId));
        }
        if (req.fileId() != null && !req.fileId().isBlank()) {
            r.setFile(files.findById(req.fileId())
                    .orElseThrow(() -> ApiException.badRequest("Ficheiro não encontrado.")));
        }

        // A medição só reagenda o programa se for a mais recente: registar uma
        // análise antiga esquecida não deve empurrar a próxima para a frente.
        if (p.getLastDoneAt() == null || performedAt.isAfter(p.getLastDoneAt())) {
            p.setLastDoneAt(performedAt);
            reschedule(p);
        }
        readings.save(r);

        String workOrderNumber = null;
        if (Boolean.TRUE.equals(req.openWorkOrder())) {
            workOrderNumber = openCorrective(orgId, userId, p, r);
        }
        warnIfNotNormal(p, r);
        // O programa deixou de estar vencido; o aviso pendente sai do caminho.
        notifications.resolve("predictive_due", p.getId());

        audit.record(orgId, userId, "predictive.reading", "PredictiveProgram", p.getId(),
                p.getAsset().getTag() + " · " + p.getTechnique().label() + " · " + req.result());

        return new ReadingRecorded(
                ReadingView.of(r, fileUrl(r)),
                ProgramView.of(p, Instant.now(), r.getResult()),
                workOrderNumber);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReadingView> history(String orgId, String programId, Pageable pageable) {
        require(orgId, programId);
        return PagedResponse.of(
                readings.findByProgramIdOrderByPerformedAtDesc(programId, pageable)
                        .map(r -> ReadingView.of(r, fileUrl(r))));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReadingView> historyForAsset(
            String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                readings.findByAssetIdOrderByPerformedAtDesc(assetId, pageable)
                        .map(r -> ReadingView.of(r, fileUrl(r))));
    }

    /** Avisa dos programas vencidos. Chamado pelo agendador. */
    @Transactional
    public int notifyDue() {
        Instant now = Instant.now();
        int sent = 0;
        for (PredictiveProgram p : programs.findAllActive()) {
            if (p.statusAt(now) != PlanTaskStatus.OVERDUE) {
                notifications.resolve("predictive_due", p.getId());
                continue;
            }
            sent += notifications.notifyManagers(NotificationService.Draft.of(
                            p.getOrganization().getId(),
                            AlertCategory.MAINTENANCE, AlertSeverity.WARNING,
                            p.getTechnique().label() + " vencida — " + p.getAsset().getTag(),
                            "Prevista para " + p.getNextDueAt() + ".",
                            "predictive_due", p.getId(),
                            "/ativos/" + p.getAsset().getId() + "/preditiva")
                    .forAsset(p.getAsset()));
        }
        return sent;
    }

    // ==== Auxiliares ====================================================
    private void apply(PredictiveProgram p, SaveProgramRequest req) {
        if (req.components() != null) p.setComponents(blankToNull(req.components()));
        if (req.goal() != null) p.setGoal(blankToNull(req.goal()));
        if (req.responsibleLabel() != null) {
            p.setResponsibleLabel(blankToNull(req.responsibleLabel()));
        }
        if (req.notes() != null) p.setNotes(blankToNull(req.notes()));
        if (req.active() != null) p.setActive(req.active());
        if (req.lastDoneAt() != null) {
            p.setLastDoneAt(req.lastDoneAt());
            reschedule(p);
        }
    }

    /** Próxima medição = última (ou agora, se nunca houve) + periodicidade. */
    /** A família do catálogo deste ativo — a mesma regra que a inspeção diária usa. */
    private static String familiaDe(Asset a) {
        String categoria = a.getAssetType() != null && a.getAssetType().getCategory() != null
                ? a.getAssetType().getCategory().name() : null;
        String tipo = a.getAssetType() != null ? a.getAssetType().getName() : null;
        return ao.autocare.modules.plan.PlanCatalog.codigoPara(categoria, tipo,
                a.getManufacturer(), a.getModel(), a.getModelYear());
    }

    /** Cria o programa se o ativo ainda não tiver essa técnica. */
    private void criarSeFaltar(String orgId, Asset asset, PredictiveTechnique technique,
            int frequencyMonths, String components, String goal) {
        if (programs.findByAssetIdAndTechnique(asset.getId(), technique).isPresent()) {
            return;
        }
        PredictiveProgram p = new PredictiveProgram();
        p.setOrganization(organizations.getReferenceById(orgId));
        p.setAsset(asset);
        p.setTechnique(technique);
        p.setFrequencyMonths(frequencyMonths);
        p.setComponents(components);
        p.setGoal(goal);
        reschedule(p);
        programs.save(p);
    }

    private void reschedule(PredictiveProgram p) {
        Instant base = p.getLastDoneAt() != null ? p.getLastDoneAt() : Instant.now();
        p.setNextDueAt(base.plus(p.getFrequencyMonths() * 30L, ChronoUnit.DAYS));
    }

    private String openCorrective(
            String orgId, String userId, PredictiveProgram p, PredictiveReading r) {

        String title = p.getTechnique().label() + " — " + resultLabel(r.getResult())
                + " em " + p.getAsset().getTag();
        StringBuilder description = new StringBuilder();
        if (r.getMeasurement() != null) {
            description.append("Medição: ").append(r.getMeasurement()).append('\n');
        }
        if (r.getFindings() != null) {
            description.append("Constatações: ").append(r.getFindings()).append('\n');
        }
        if (r.getRecommendation() != null) {
            description.append("Recomendação: ").append(r.getRecommendation());
        }

        WorkOrderView wo = workOrders.create(orgId, userId, new CreateWorkOrderRequest(
                p.getAsset().getId(),
                WorkOrderType.CORRECTIVE,
                title,
                description.toString().trim(),
                r.getResult() == PredictiveResult.CRITICAL
                        ? WorkOrderPriority.URGENT : WorkOrderPriority.HIGH,
                null, null, null, null, null,
                // Prazo, estimativas e restantes campos da ficha ficam por
                // preencher: quem abre a ordem a partir de uma medição
                // preditiva sabe o resultado, não sabe o orçamento nem as horas.
                null, null, null,
                // Sem código de sistema: o programa preditivo tem componentes
                // em texto livre, e mapeá-los a um sistema por adivinhação
                // estragaria a deteção de avarias repetidas.
                null,
                null, null, null, null, null, null, null));
        r.setWorkOrderId(wo.id());
        return wo.number();
    }

    private void warnIfNotNormal(PredictiveProgram p, PredictiveReading r) {
        if (r.getResult() == PredictiveResult.NORMAL) {
            return;
        }
        boolean critical = r.getResult() == PredictiveResult.CRITICAL;
        notifications.notifyManagers(NotificationService.Draft.of(
                        p.getOrganization().getId(),
                        AlertCategory.MAINTENANCE,
                        critical ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                        p.getTechnique().label() + " — " + resultLabel(r.getResult())
                                + " em " + p.getAsset().getTag(),
                        r.getRecommendation() != null ? r.getRecommendation() : r.getFindings(),
                        "predictive_reading", r.getId(),
                        "/ativos/" + p.getAsset().getId() + "/preditiva")
                .forAsset(p.getAsset()));
    }

    private PredictiveResult lastResult(PredictiveProgram p) {
        return readings.findByProgramIdOrderByPerformedAtDesc(p.getId(), PageRequest.of(0, 1))
                .stream().findFirst().map(PredictiveReading::getResult).orElse(null);
    }

    private String fileUrl(PredictiveReading r) {
        return r.getFile() != null ? fileUrls.signed(r.getFile().getId()) : null;
    }

    private static String resultLabel(PredictiveResult result) {
        return switch (result) {
            case NORMAL -> "normal";
            case ATTENTION -> "atenção";
            case CRITICAL -> "crítico";
        };
    }

    private static String standardComponents(PredictiveTechnique technique) {
        return switch (technique) {
            case VIBRATION -> "Rolamentos, bombas, motores elétricos";
            case THERMOGRAPHY -> "Painéis elétricos, ligações, motores";
            case OIL_ANALYSIS -> "Motor, sistema hidráulico, transmissão";
            default -> null;
        };
    }

    private static String standardGoal(PredictiveTechnique technique) {
        return switch (technique) {
            case VIBRATION -> "Detetar desgaste e desalinhamento antes da falha";
            case THERMOGRAPHY -> "Detetar sobreaquecimento e mau contacto";
            case OIL_ANALYSIS -> "Detetar desgaste interno e contaminação";
            default -> null;
        };
    }

    private PlanTaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PlanTaskStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado desconhecido: " + status);
        }
    }

    private PredictiveProgram require(String orgId, String id) {
        return programs.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Programa preditivo não encontrado."));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
