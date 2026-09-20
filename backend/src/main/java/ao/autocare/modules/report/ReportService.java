package ao.autocare.modules.report;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetCriticality;
import ao.autocare.domain.AssetDocument;
import ao.autocare.domain.PredictiveProgram;
import ao.autocare.domain.StockItem;
import ao.autocare.domain.Trip;
import ao.autocare.domain.WorkOrder;
import ao.autocare.modules.kpi.KpiService;
import ao.autocare.modules.kpi.dto.KpiDtos.KpiReport;
import ao.autocare.modules.kpi.dto.KpiDtos.Metric;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ProgramView;
import ao.autocare.repo.AssetCriticalityRepository;
import ao.autocare.repo.AssetDocumentRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.PredictiveProgramRepository;
import ao.autocare.repo.StockItemRepository;
import ao.autocare.repo.TripRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.time.Duration;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.FuelRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportação de dados para folha de cálculo.
 *
 * <p>Um CMMS é consultado dentro da aplicação, mas os relatórios que sobem à
 * administração e os que vão para auditoria acabam sempre em Excel. Estes
 * ficheiros são o formato em que a informação sai do sistema, por isso trazem
 * os nomes das colunas em português e os valores já legíveis — datas no fuso de
 * Luanda, decimais com vírgula — em vez de identificadores internos.
 */
@Service
public class ReportService {

    /** Tecto por exportação, para um pedido não puxar a base de dados inteira. */
    private static final int MAX_ROWS = 20_000;

    private final AssetRepository assets;
    private final AssetCriticalityRepository criticalities;
    private final WorkOrderRepository workOrders;
    private final ao.autocare.repo.AssetMeterRepository meters;
    private final StockItemRepository stockItems;
    private final TripRepository trips;
    private final AssetDocumentRepository documents;
    private final PredictiveProgramRepository predictive;
    private final ao.autocare.repo.FuelRecordRepository fuelRecords;
    private final ao.autocare.repo.FuelAnomalyRepository fuelAnomalies;
    private final KpiService kpis;
    private final ao.autocare.repo.TyreRepository tyres;
    private final ao.autocare.repo.AuditLogRepository auditLogs;
    private final ao.autocare.repo.UserRepository usersRepo;

    public ReportService(
            AssetRepository assets,
            AssetCriticalityRepository criticalities,
            WorkOrderRepository workOrders,
            StockItemRepository stockItems,
            TripRepository trips,
            AssetDocumentRepository documents,
            PredictiveProgramRepository predictive,
            KpiService kpis,
            ao.autocare.repo.AssetMeterRepository meters,
            ao.autocare.repo.FuelRecordRepository fuelRecords,
            ao.autocare.repo.FuelAnomalyRepository fuelAnomalies,
            ao.autocare.repo.TyreRepository tyres,
            ao.autocare.repo.AuditLogRepository auditLogs,
            ao.autocare.repo.UserRepository usersRepo) {
        this.assets = assets;
        this.criticalities = criticalities;
        this.workOrders = workOrders;
        this.meters = meters;
        this.stockItems = stockItems;
        this.trips = trips;
        this.documents = documents;
        this.predictive = predictive;
        this.fuelRecords = fuelRecords;
        this.fuelAnomalies = fuelAnomalies;
        this.kpis = kpis;
        this.tyres = tyres;
        this.auditLogs = auditLogs;
        this.usersRepo = usersRepo;
    }

    @Transactional(readOnly = true)
    public CsvWriter assets(String orgId) {
        CsvWriter csv = new CsvWriter("Etiqueta", "Nome", "Tipo", "Local", "Estado",
                "Criticidade", "Fabricante", "Modelo", "Nº de série", "Ano", "Matrícula",
                "Responsável", "Arquivado");

        for (Asset a : assets.findByOrganizationId(orgId)) {
            AssetCriticality crit = criticalities.findByAssetId(a.getId()).orElse(null);
            csv.row(
                    a.getTag(), a.getName(),
                    a.getAssetType() != null ? a.getAssetType().getName() : null,
                    a.getLocation() != null ? a.getLocation().getName() : null,
                    a.getStatus() != null ? a.getStatus().name() : null,
                    crit != null && crit.getOverall() != null ? crit.getOverall().name() : null,
                    a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getModelYear(),
                    a.getPlate(), a.getResponsibleLabel(),
                    a.isArchived() ? "Sim" : "Não");
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter workOrders(String orgId) {
        CsvWriter csv = new CsvWriter("Número", "Ativo", "Tipo", "Estado", "Prioridade",
                "Título", "Sistema", "Responsável", "Oficina", "Execução",
                "Aberta em", "Prazo", "Iniciada em", "Concluída em", "Fechada em",
                "Prazo cumprido", "Horas de mão de obra", "Custo de mão de obra",
                "Custo de peças", "Custo externo", "Custo total", "Orçamentado",
                "Aprovado", "Horas paradas", "Custo da paragem", "Moeda",
                "Causa raiz", "Resolução");

        for (WorkOrder w : workOrders.findByOrganizationIdOrderByOpenedAtDesc(
                orgId, PageRequest.of(0, MAX_ROWS))) {
            csv.row(
                    w.getNumber(),
                    w.getAsset() != null ? w.getAsset().getTag() : null,
                    w.getType().label(), w.getStatus().label(), w.getPriority().name(),
                    w.getTitle(), w.getSystemCode(), w.getAssignedToLabel(),
                    w.getSupplier() != null ? w.getSupplier().getName() : null,
                    w.getExecution().name(),
                    w.getOpenedAt(), w.getDueAt(), w.getStartedAt(),
                    w.getCompletedAt(), w.getClosedAt(),
                    w.getSlaMet() == null ? null : (w.getSlaMet() ? "Sim" : "Não"),
                    w.getTotalLaborHours(), w.getTotalLaborCost(),
                    w.getTotalPartsCost(), w.getTotalExternalCost(), w.getTotalCost(),
                    w.getEstimatedCost(), w.getApprovedAmount(),
                    w.getDowntimeHours(), w.getDowntimeCost(), w.getCurrency(),
                    w.getRootCause(), w.getResolution());
        }
        return csv;
    }

    /**
     * Custo de manutencao por ativo.
     *
     * <p>E o relatorio que responde a pergunta que decide o destino de uma
     * viatura: vale a pena continuar a repara-la? Por isso leva o custo por km
     * e os dias parada, e nao apenas o total gasto.
     */
    @Transactional(readOnly = true)
    public CsvWriter maintenanceByAsset(String orgId) {
        CsvWriter csv = new CsvWriter("Ativo", "Nome", "Local", "Ordens",
                "Corretivas", "Preventivas", "Custo de mao de obra", "Custo de pecas",
                "Custo externo", "Custo total", "Moeda", "Horas paradas",
                "Custo da paragem", "Km/h no medidor", "Custo por km ou hora");

        Map<String, List<WorkOrder>> porAtivo = new LinkedHashMap<>();
        for (WorkOrder w : workOrders.findByOrganizationIdOrderByOpenedAtDesc(
                orgId, PageRequest.of(0, MAX_ROWS))) {
            if (w.getAsset() != null) {
                porAtivo.computeIfAbsent(w.getAsset().getId(), k -> new ArrayList<>()).add(w);
            }
        }

        for (List<WorkOrder> lista : porAtivo.values()) {
            var asset = lista.get(0).getAsset();
            BigDecimal mao = BigDecimal.ZERO;
            BigDecimal pecas = BigDecimal.ZERO;
            BigDecimal externo = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal horasParado = BigDecimal.ZERO;
            BigDecimal custoParagem = BigDecimal.ZERO;
            int corretivas = 0;
            int preventivas = 0;
            String moeda = "AOA";

            for (WorkOrder w : lista) {
                mao = mao.add(orZero(w.getTotalLaborCost()));
                pecas = pecas.add(orZero(w.getTotalPartsCost()));
                externo = externo.add(orZero(w.getTotalExternalCost()));
                total = total.add(orZero(w.getTotalCost()));
                horasParado = horasParado.add(orZero(w.getDowntimeHours()));
                custoParagem = custoParagem.add(orZero(w.getDowntimeCost()));
                moeda = w.getCurrency();
                if (w.getType() == ao.autocare.domain.enums.Enums.WorkOrderType.CORRECTIVE
                        || w.getType() == ao.autocare.domain.enums.Enums.WorkOrderType.EMERGENCY) {
                    corretivas++;
                } else if (w.getType()
                        == ao.autocare.domain.enums.Enums.WorkOrderType.PREVENTIVE) {
                    preventivas++;
                }
            }

            BigDecimal medidor = meters.findByAssetId(asset.getId()).stream()
                    .filter(m -> m.isPrimary())
                    .map(m -> m.getCurrentValue())
                    .findFirst().orElse(BigDecimal.ZERO);
            // Nulo, nao zero: sem medidor nao ha custo por km que se calcule, e
            // zero leria-se como "nao custa nada andar com esta viatura".
            BigDecimal porUnidade = medidor.signum() > 0
                    ? total.divide(medidor, 2, RoundingMode.HALF_UP) : null;

            csv.row(asset.getTag(), asset.getName(),
                    asset.getLocation() != null ? asset.getLocation().getName() : null,
                    lista.size(), corretivas, preventivas,
                    mao, pecas, externo, total, moeda,
                    horasParado, custoParagem, medidor, porUnidade);
        }
        return csv;
    }

    /** Quanto se gastou em cada oficina, e quanto tempo elas demoram. */
    @Transactional(readOnly = true)
    public CsvWriter maintenanceBySupplier(String orgId) {
        CsvWriter csv = new CsvWriter("Oficina", "NIF", "Cidade", "Ordens",
                "Custo externo total", "Moeda", "Media de dias por ordem");

        Map<String, List<WorkOrder>> porOficina = new LinkedHashMap<>();
        for (WorkOrder w : workOrders.findByOrganizationIdOrderByOpenedAtDesc(
                orgId, PageRequest.of(0, MAX_ROWS))) {
            if (w.getSupplier() != null) {
                porOficina.computeIfAbsent(w.getSupplier().getId(), k -> new ArrayList<>())
                        .add(w);
            }
        }

        for (List<WorkOrder> lista : porOficina.values()) {
            var f = lista.get(0).getSupplier();
            BigDecimal total = BigDecimal.ZERO;
            long dias = 0;
            int comDatas = 0;
            String moeda = "AOA";

            for (WorkOrder w : lista) {
                total = total.add(orZero(w.getTotalExternalCost()));
                moeda = w.getCurrency();
                if (w.getStartedAt() != null && w.getCompletedAt() != null) {
                    dias += java.time.Duration
                            .between(w.getStartedAt(), w.getCompletedAt()).toDays();
                    comDatas++;
                }
            }
            csv.row(f.getName(), f.getTaxId(), f.getCity(), lista.size(),
                    total, moeda,
                    comDatas > 0 ? BigDecimal.valueOf(dias)
                            .divide(BigDecimal.valueOf(comDatas), 1, RoundingMode.HALF_UP)
                            : null);
        }
        return csv;
    }

    /** Quanto tempo cada viatura esteve parada, e o que isso custou. */
    @Transactional(readOnly = true)
    public CsvWriter maintenanceDowntime(String orgId) {
        CsvWriter csv = new CsvWriter("Ordem", "Ativo", "Tipo", "Parou em", "Voltou em",
                "Horas paradas", "Custo da paragem", "Moeda", "Motivo");

        for (WorkOrder w : workOrders.findByOrganizationIdOrderByOpenedAtDesc(
                orgId, PageRequest.of(0, MAX_ROWS))) {
            if (w.getDowntimeHours() == null) {
                continue;
            }
            csv.row(w.getNumber(),
                    w.getAsset() != null ? w.getAsset().getTag() : null,
                    w.getType().label(),
                    w.getDowntimeStart(), w.getDowntimeEnd(),
                    w.getDowntimeHours(), w.getDowntimeCost(), w.getCurrency(),
                    w.getTitle());
        }
        return csv;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Todos os abastecimentos, com o consumo e o que ficou por explicar.
     *
     * <p>E o ficheiro que uma empresa leva para a reuniao mensal: nao basta
     * saber quanto se gastou, e preciso ver linha a linha onde o consumo saiu
     * do normal e quanto disso e dinheiro.
     */
    @Transactional(readOnly = true)
    public CsvWriter fuel(String orgId) {
        CsvWriter csv = new CsvWriter("Data", "Ativo", "Nome", "Matricula", "Motorista",
                "Filial", "Posto", "Litros", "Preco por litro", "Total", "Moeda",
                "Contador", "Deposito cheio", "Distancia ou horas", "Consumo",
                "Unidade do consumo",
                "Cartao", "Fatura", "Combustivel", "Anomalias", "Por explicar");

        for (FuelRecord f : fuelRecords.findByOrganizationIdOrderByFilledAtDesc(
                orgId, PageRequest.of(0, MAX_ROWS))) {

            BigDecimal porExplicar = BigDecimal.ZERO;
            int anomalias = 0;
            for (FuelAnomaly a : fuelAnomalies.findByFuelRecordId(f.getId())) {
                anomalias++;
                if (a.getCostAtRisk() != null) {
                    porExplicar = porExplicar.add(a.getCostAtRisk());
                }
            }

            csv.row(f.getFilledAt(),
                    f.getAsset() != null ? f.getAsset().getTag() : null,
                    f.getAsset() != null ? f.getAsset().getName() : null,
                    f.getAsset() != null ? f.getAsset().getPlate() : null,
                    f.getDriver() != null ? f.getDriver().getName() : f.getDriverLabel(),
                    f.getBranch() != null ? f.getBranch().getName() : null,
                    f.getStation(),
                    f.getLiters(), f.getPricePerLiter(), f.getTotalCost(), f.getCurrency(),
                    f.getMeterValue(),
                    f.isFullTank() ? "Sim" : "Nao",
                    f.getDistanceOrHours(), f.getConsumption(), f.getConsumptionUnit(),
                    f.getCardNumber(), f.getInvoiceNumber(),
                    f.getFuelType() != null ? f.getFuelType().name() : null,
                    anomalias > 0 ? anomalias : null,
                    porExplicar.signum() > 0 ? porExplicar : null);
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter stock(String orgId) {
        CsvWriter csv = new CsvWriter("Peça", "Nº de peça", "Sistema", "Categoria", "Armazém",
                "Quantidade", "Unidade", "Stock mínimo", "Custo médio", "Moeda", "Abaixo do mínimo");

        for (StockItem item : stockItems.findAllForOrg(orgId)) {
            java.math.BigDecimal minimum = item.getMinQuantity() != null
                    ? item.getMinQuantity() : item.getPart().getMinQuantity();
            boolean low = minimum != null && minimum.signum() > 0
                    && item.getQuantity().compareTo(minimum) < 0;
            csv.row(
                    item.getPart().getName(), item.getPart().getPartNumber(),
                    item.getPart().getSystemCode(),
                    item.getPart().getCategory() != null
                            ? item.getPart().getCategory().name() : null,
                    item.getWarehouse().getName(), item.getQuantity(),
                    item.getPart().getUnit(), minimum,
                    item.getPart().getAverageCost(), item.getPart().getCurrency(),
                    low ? "Sim" : "Não");
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter trips(String orgId, Instant from, Instant to) {
        CsvWriter csv = new CsvWriter("Ativo", "Início", "Fim", "Duração (min)",
                "Distância (km)", "Velocidade máxima (km/h)", "Posições", "Em curso");

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        for (Trip t : trips.findByOrganizationIdOrderByStartedAtDesc(
                orgId, PageRequest.of(0, MAX_ROWS))) {
            if (t.getStartedAt().isBefore(start) || t.getStartedAt().isAfter(end)) {
                continue;
            }
            csv.row(
                    t.getAsset().getTag(), t.getStartedAt(), t.getEndedAt(),
                    t.getDurationMinutes(), t.getDistanceKm(), t.getMaxSpeedKph(),
                    t.getPositionCount(), t.isOpen() ? "Sim" : "Não");
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter documents(String orgId) {
        CsvWriter csv = new CsvWriter("Ativo", "Tipo", "Título", "Referência", "Emissor",
                "Emitido em", "Válido até", "Dias restantes", "Estado");

        Instant now = Instant.now();
        // Janela larga: a exportação serve para ver tudo, não só o urgente.
        for (AssetDocument d : documents.findExpiringUntil(orgId, now.plus(Duration.ofDays(3650)))) {
            var status = ao.autocare.common.ExpiryCalculator.status(d.getExpiresAt(), now);
            csv.row(
                    d.getAsset().getTag(), d.getKind().label(), d.getTitle(),
                    d.getReference(), d.getIssuer(), d.getIssuedAt(), d.getExpiresAt(),
                    status != null ? status.daysRemaining() : null,
                    status != null ? status.label() : "Sem validade");
        }
        return csv;
    }

    /** Pneus: a pergunta da compra — que marca dura mais e a quanto sai o km. */
    @Transactional(readOnly = true)
    public CsvWriter tyres(String orgId) {
        CsvWriter csv = new CsvWriter("Ativo", "Posição", "Marca", "Modelo", "Medida", "Nº de série",
                "Estado", "Montado em", "Contador na montagem", "Desmontado em", "Contador na saída",
                "Motivo", "Km/h percorridos", "Custo", "Custo por km/h", "Último sulco (mm)",
                "Última pressão (bar)", "Alerta");
        java.util.Map<String, java.math.BigDecimal> contadores = new java.util.HashMap<>();
        for (ao.autocare.domain.Tyre t : tyres.findByOrganizationIdOrderByStatusAscUpdatedAtDesc(orgId)) {
            java.math.BigDecimal contador = null;
            if (t.getAsset() != null) {
                contador = contadores.computeIfAbsent(t.getAsset().getId(), id -> meters.findByAssetId(id).stream()
                        .filter(ao.autocare.domain.AssetMeter::isPrimary).findFirst()
                        .map(ao.autocare.domain.AssetMeter::getCurrentValue).orElse(null));
            }
            csv.row(
                    t.getAsset() != null ? t.getAsset().getTag() : null,
                    t.getPosition(), t.getBrand(), t.getModel(), t.getSize(), t.getSerialNumber(),
                    switch (t.getStatus()) { case INSTALLED -> "Montado"; case STOCK -> "Em stock"; case RETIRED -> "Abatido"; },
                    t.getInstalledAt(), t.getInstalledMeter(), t.getRemovedAt(), t.getRemovedMeter(),
                    t.getRemovalReason() != null ? t.getRemovalReason().name() : null,
                    t.distanceRun(contador), t.getCost(), t.costPerUnit(contador),
                    t.getLastTreadMm(), t.getLastPressure(), t.alerta());
        }
        return csv;
    }

    /**
     * O registo de auditoria exportável: quem fez o quê e quando. É o que um
     * auditor externo pede — e o que uma certificação ISO exige que exista.
     */
    @Transactional(readOnly = true)
    public CsvWriter audit(String orgId, Instant from, Instant to) {
        Instant fim = to != null ? to : Instant.now();
        Instant inicio = from != null ? from : fim.minus(90, java.time.temporal.ChronoUnit.DAYS);
        CsvWriter csv = new CsvWriter("Data e hora", "Utilizador", "Ação", "Entidade", "Identificador",
                "Resumo", "Endereço IP");
        java.util.Map<String, String> nomes = new java.util.HashMap<>();
        org.springframework.data.jpa.domain.Specification<ao.autocare.domain.AuditLog> spec =
                (root, q, cb) -> cb.and(
                        cb.equal(root.get("organizationId"), orgId),
                        cb.greaterThanOrEqualTo(root.get("createdAt"), inicio),
                        cb.lessThan(root.get("createdAt"), fim));
        for (ao.autocare.domain.AuditLog a : auditLogs.findAll(spec,
                org.springframework.data.domain.PageRequest.of(0, MAX_ROWS,
                        org.springframework.data.domain.Sort.by("createdAt").descending()))) {
            String quem = a.getUserId() == null ? "sistema" : nomes.computeIfAbsent(a.getUserId(),
                    id -> usersRepo.findById(id).map(ao.autocare.domain.User::getName).orElse("(utilizador removido)"));
            csv.row(a.getCreatedAt(), quem, a.getAction(), a.getEntityType(), a.getEntityId(), a.getSummary(), a.getIp());
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter predictive(String orgId) {
        CsvWriter csv = new CsvWriter("Ativo", "Técnica", "Periodicidade", "Componentes",
                "Objetivo", "Responsável", "Última medição", "Próxima medição",
                "Dias restantes", "Estado");

        Instant now = Instant.now();
        for (PredictiveProgram p : predictive.findActive(orgId)) {
            csv.row(
                    p.getAsset().getTag(), p.getTechnique().label(),
                    ProgramView.frequencyLabel(p.getFrequencyMonths()),
                    p.getComponents(), p.getGoal(), p.getResponsibleLabel(),
                    p.getLastDoneAt(), p.getNextDueAt(), p.remainingDays(now),
                    p.statusAt(now).name());
        }
        return csv;
    }

    /**
     * Indicadores do período, no formato do documento de referência: uma linha
     * por indicador, com meta e se está a ser cumprida.
     */
    @Transactional(readOnly = true)
    public CsvWriter kpis(String orgId, String assetId, Instant from, Instant to) {
        KpiReport report = kpis.report(orgId, assetId, from, to);

        CsvWriter csv = new CsvWriter("Indicador", "Valor", "Unidade", "Meta", "Sentido",
                "Cumpre", "Fórmula");
        for (Metric m : report.metrics()) {
            csv.row(m.name(),
                    m.value() != null ? java.math.BigDecimal.valueOf(m.value()) : null,
                    m.unit(),
                    m.target() != null ? java.math.BigDecimal.valueOf(m.target()) : null,
                    "MIN".equals(m.targetDirection()) ? "Mínimo" : "Máximo",
                    m.meetsTarget() ? "Sim" : "Não",
                    m.formula());
        }

        // Os números que alimentam as fórmulas, para o relatório se poder auditar.
        csv.row();
        csv.row("Dados do período");
        csv.row("Âmbito", report.scopeName() != null ? report.scopeName() : "Toda a frota");
        csv.row("De", report.from());
        csv.row("Até", report.to());
        csv.row("Horas de operação", java.math.BigDecimal.valueOf(report.operatingHours()));
        csv.row("Quilómetros percorridos", java.math.BigDecimal.valueOf(report.operatingKm()));
        csv.row("Horas planeadas", java.math.BigDecimal.valueOf(report.plannedHours()));
        csv.row("Horas de paragem", java.math.BigDecimal.valueOf(report.downtimeHours()));
        csv.row("Nº de falhas", report.failures());
        csv.row("Nº de reparações", report.repairs());
        csv.row("Horas de reparação", java.math.BigDecimal.valueOf(report.totalRepairHours()));
        csv.row("Ordens planeadas", report.plannedOrders());
        csv.row("Ordens executadas", report.executedOrders());
        return csv;
    }

    /** Nome de ficheiro com a data, para não ficarem cinco "relatorio.csv" na pasta. */
    public String fileName(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw ApiException.badRequest("Relatório desconhecido.");
        }
        return prefix + "-" + java.time.LocalDate.now(java.time.ZoneId.of("Africa/Luanda"));
    }
}
