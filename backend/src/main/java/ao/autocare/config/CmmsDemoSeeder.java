package ao.autocare.config;

import ao.autocare.config.ReferencePlanData.ChecklistItemRow;
import ao.autocare.config.ReferencePlanData.PlanTaskRow;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetCriticality;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.AssetPlan;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.AssetSystem;
import ao.autocare.domain.AssetType;
import ao.autocare.domain.ChecklistItem;
import ao.autocare.domain.ChecklistTemplate;
import ao.autocare.domain.Location;
import ao.autocare.domain.MaintenancePlan;
import ao.autocare.domain.MeterReading;
import ao.autocare.domain.Organization;
import ao.autocare.domain.PlanTask;
import ao.autocare.domain.PlanTaskTrigger;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.LocationKind;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.MeterReadingSource;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import ao.autocare.domain.enums.Enums.VerificationType;
import ao.autocare.modules.plan.PlanScheduleCalculator;
import ao.autocare.modules.plan.PlanScheduleCalculator.TriggerSpec;
import ao.autocare.repo.AssetCriticalityRepository;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetPlanRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.ChecklistTemplateRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.MaintenancePlanRepository;
import ao.autocare.repo.MeterReadingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Dados de demonstração do CMMS (Fase 2). Reproduz o equipamento do documento de
 * referência: uma retroescavadora de obra, mais alguns ativos para
 * uma frota realista. As fases seguintes acrescentam planos, ordens e peças.
 */
@Component
public class CmmsDemoSeeder {

    /** Os 8 sistemas do documento de referência. */
    private static final List<String[]> SYSTEMS = List.of(
            new String[] {"ENGINE", "Motor"},
            new String[] {"HYDRAULIC", "Sistema Hidráulico"},
            new String[] {"FUEL", "Sistema de Combustível"},
            new String[] {"TRANSMISSION", "Sistema de Transmissão"},
            new String[] {"AXLES", "Eixos e Diferenciais"},
            new String[] {"ELECTRICAL", "Sistema Elétrico"},
            new String[] {"BRAKES", "Sistema de Travagem"},
            new String[] {"STRUCTURE", "Estrutura e Chassi"});

    private final LocationRepository locations;
    private final AssetTypeRepository assetTypes;
    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final MeterReadingRepository readings;
    private final AssetCriticalityRepository criticalities;
    private final ChecklistTemplateRepository checklistTemplates;
    private final MaintenancePlanRepository maintenancePlans;
    private final AssetPlanRepository assetPlans;

    public CmmsDemoSeeder(
            LocationRepository locations,
            AssetTypeRepository assetTypes,
            AssetRepository assets,
            AssetMeterRepository meters,
            MeterReadingRepository readings,
            AssetCriticalityRepository criticalities,
            ChecklistTemplateRepository checklistTemplates,
            MaintenancePlanRepository maintenancePlans,
            AssetPlanRepository assetPlans) {
        this.locations = locations;
        this.assetTypes = assetTypes;
        this.assets = assets;
        this.meters = meters;
        this.readings = readings;
        this.criticalities = criticalities;
        this.checklistTemplates = checklistTemplates;
        this.maintenancePlans = maintenancePlans;
        this.assetPlans = assetPlans;
    }

    public void seed(Organization org, User responsible) {
        Location site = location(org, null, "Obra Luanda Sul", "OBR-01", LocationKind.SITE);
        Location yard = location(org, site, "Parque de Máquinas", "PRQ-01", LocationKind.YARD);
        Location warehouse = location(org, site, "Armazém de Peças", "ARM-01", LocationKind.WAREHOUSE);

        AssetType backhoe = assetType(org, "Retroescavadora", AssetCategory.MACHINE, MeterKind.HOURMETER);
        AssetType generator = assetType(org, "Gerador Diesel", AssetCategory.GENERATOR, MeterKind.HOURMETER);
        AssetType truck = assetType(org, "Camião", AssetCategory.VEHICLE, MeterKind.ODOMETER);

        // Ativo principal do documento de referência
        Asset re001 = new Asset();
        re001.setOrganization(org);
        re001.setAssetType(backhoe);
        re001.setLocation(yard);
        re001.setResponsibleUser(responsible);
        re001.setTag("RE-001");
        re001.setName("Retroescavadora");
        re001.setManufacturer("Volvo");
        re001.setModel("BL71B");
        re001.setSerialNumber("VCE0BL71C00012345");
        re001.setModelYear(2023);
        re001.setResponsibleLabel("Departamento de Manutenção");
        re001.setObjective("Estabelecer as atividades de manutenção preventiva com a finalidade de "
                + "garantir a máxima disponibilidade, confiabilidade e vida útil da retroescavadora, "
                + "reduzindo falhas inesperadas, custos de reparação e tempos de parada.");
        re001.setStatus(AssetStatus.OPERATIONAL);
        re001.setAcquisitionDate(Instant.now().minus(400, ChronoUnit.DAYS));
        re001.setAcquisitionValue(new BigDecimal("48500000.00"));
        assets.save(re001);
        AssetMeter re001Meter = hourmeter(re001, new BigDecimal("1240"), 90);
        // Documento de referência: Produção 4★, Segurança 5★, Financeiro 5★ → CRÍTICA
        criticality(re001, 4, 5, 5);

        // Checklist de inspeção diária + plano de manutenção do documento de referência
        dailyChecklist(org, backhoe);
        MaintenancePlan catPlan = catMaintenancePlan(org, backhoe);
        assignPlan(org, re001, re001Meter, catPlan);

        // Frota adicional
        Asset ger1 = simpleAsset(org, generator, warehouse, "GER-001", "Gerador Cummins 250 kVA",
                "Cummins", "C250 D5", 2022);
        hourmeter(ger1, new BigDecimal("3120"), 45);
        criticality(ger1, 4, 3, 4);

        Asset cam1 = simpleAsset(org, truck, yard, "CAM-001", "Camião Basculante Volvo",
                "Volvo", "FMX 440", 2021);
        odometer(cam1, new BigDecimal("128400"), 20);
        criticality(cam1, 3, 4, 3);
    }

    // ------------------------------------------------------------------
    private Location location(Organization org, Location parent, String name, String code, LocationKind kind) {
        Location l = new Location();
        l.setOrganization(org);
        l.setParent(parent);
        l.setName(name);
        l.setCode(code);
        l.setKind(kind);
        return locations.save(l);
    }

    private AssetType assetType(Organization org, String name, AssetCategory category, MeterKind meter) {
        AssetType t = new AssetType();
        t.setOrganization(org);
        t.setName(name);
        t.setCategory(category);
        t.setPrimaryMeter(meter);
        int order = 1;
        for (String[] s : SYSTEMS) {
            AssetSystem sys = new AssetSystem();
            sys.setCode(s[0]);
            sys.setName(s[1]);
            sys.setSortOrder(order++);
            t.addSystem(sys);
        }
        return assetTypes.save(t);
    }

    private Asset simpleAsset(Organization org, AssetType type, Location location,
                             String tag, String name, String manufacturer, String model, int year) {
        Asset a = new Asset();
        a.setOrganization(org);
        a.setAssetType(type);
        a.setLocation(location);
        a.setTag(tag);
        a.setName(name);
        a.setManufacturer(manufacturer);
        a.setModel(model);
        a.setModelYear(year);
        a.setStatus(AssetStatus.OPERATIONAL);
        return assets.save(a);
    }

    private AssetMeter hourmeter(Asset asset, BigDecimal current, int daysOfHistory) {
        return buildMeter(asset, MeterKind.HOURMETER, "h", current, daysOfHistory, new BigDecimal("7.5"));
    }

    private AssetMeter odometer(Asset asset, BigDecimal current, int daysOfHistory) {
        return buildMeter(asset, MeterKind.ODOMETER, "km", current, daysOfHistory, new BigDecimal("180"));
    }

    private AssetMeter buildMeter(Asset asset, MeterKind kind, String unit, BigDecimal current,
                            int daysOfHistory, BigDecimal perDay) {
        AssetMeter m = new AssetMeter();
        m.setAsset(asset);
        m.setKind(kind);
        m.setUnit(unit);
        m.setPrimary(true);
        BigDecimal start = current.subtract(perDay.multiply(BigDecimal.valueOf(daysOfHistory)));
        if (start.signum() < 0) start = BigDecimal.ZERO;
        m.setCurrentValue(current);
        m.setLastReadingAt(Instant.now());
        m.setDailyAverage(perDay);
        meters.save(m);

        MeterReading first = reading(m, start, Instant.now().minus(daysOfHistory, ChronoUnit.DAYS));
        MeterReading last = reading(m, current, Instant.now());
        first.setDelta(BigDecimal.ZERO);
        last.setDelta(current.subtract(start));
        readings.save(first);
        readings.save(last);
        return m;
    }

    // ---- Checklist + plano do documento de referência -----------------
    private void dailyChecklist(Organization org, AssetType assetType) {
        ChecklistTemplate t = new ChecklistTemplate();
        t.setOrganization(org);
        t.setAssetType(assetType);
        t.setName("Inspeção diária (antes do arranque)");
        t.setEstimatedMinutes(15);
        int order = 1;
        for (ChecklistItemRow row : ReferencePlanData.DAILY_CHECKLIST) {
            ChecklistItem item = new ChecklistItem();
            item.setText(row.text());
            item.setVerification(VerificationType.valueOf(row.verification()));
            item.setCritical(row.critical());
            item.setSortOrder(order++);
            t.addItem(item);
        }
        checklistTemplates.save(t);
    }

    private MaintenancePlan catMaintenancePlan(Organization org, AssetType assetType) {
        MaintenancePlan plan = new MaintenancePlan();
        plan.setOrganization(org);
        plan.setAssetType(assetType);
        plan.setName("Plano de Manutenção Preventiva — Retroescavadora");
        plan.setDescription("Lubrificação a cada 50 h e manutenção por horas (250 / 500 / 1000 / 2000 h) "
                + "em 8 sistemas, conforme o documento de referência.");
        plan.setNotes("Utilizar apenas peças originais do fabricante. Seguir o manual do operador e de "
                + "serviço. Registar todas as manutenções no sistema (OM).");

        int order = 1;
        // Lubrificação geral a cada 50 h
        PlanTask lube = new PlanTask();
        lube.setSystemCode("HYDRAULIC");
        lube.setSystemName("Lubrificação");
        lube.setTitle(ReferencePlanData.LUBE_TITLE);
        lube.setTools(ReferencePlanData.LUBE_TOOLS);
        lube.setSortOrder(order++);
        lube.addTrigger(meterTrigger(ReferencePlanData.LUBE_HOURS));
        plan.addTask(lube);

        for (PlanTaskRow row : ReferencePlanData.PLAN_TASKS) {
            PlanTask task = new PlanTask();
            task.setSystemCode(row.systemCode());
            task.setSystemName(row.systemName());
            task.setTitle(row.title());
            task.setInstructions(row.title());
            task.setSortOrder(order++);
            task.addTrigger(meterTrigger(row.hours()));
            plan.addTask(task);
        }
        return maintenancePlans.save(plan);
    }

    private PlanTaskTrigger meterTrigger(int hours) {
        PlanTaskTrigger tr = new PlanTaskTrigger();
        tr.setTriggerType(PlanTriggerType.METER_INTERVAL);
        tr.setMeterKind(MeterKind.HOURMETER);
        tr.setIntervalValue(BigDecimal.valueOf(hours));
        return tr;
    }

    private void assignPlan(Organization org, Asset asset, AssetMeter meter, MaintenancePlan plan) {
        BigDecimal current = meter.getCurrentValue();
        Instant now = Instant.now();

        AssetPlan ap = new AssetPlan();
        ap.setOrganization(org);
        ap.setAsset(asset);
        ap.setPlan(plan);
        ap.setPlanName(plan.getName());
        ap.setAssignedAt(now);

        for (PlanTask task : plan.getTasks()) {
            AssetPlanTask apt = new AssetPlanTask();
            apt.setTask(task);
            apt.setTitle(task.getTitle());
            apt.setSystemName(task.getSystemName());

            // Última execução simulada: deixa alguma "sobra" para cada tarefa.
            BigDecimal interval = task.getTriggers().get(0).getIntervalValue();
            BigDecimal remaining = interval.equals(BigDecimal.valueOf(50))
                    ? new BigDecimal("6")                        // lubrificação quase a vencer
                    : interval.multiply(new BigDecimal("0.55")); // resto a meio do ciclo
            BigDecimal lastDone = current.subtract(interval).add(remaining).max(BigDecimal.ZERO);
            apt.setLastDoneMeter(lastDone);
            apt.setLastDoneAt(now.minus(30, ChronoUnit.DAYS));

            var spec = new TriggerSpec(PlanTriggerType.METER_INTERVAL, MeterKind.HOURMETER, interval, null);
            var p = PlanScheduleCalculator.project(
                    List.of(spec), apt.getLastDoneAt(), lastDone,
                    current, MeterKind.HOURMETER, meter.getDailyAverage(), now);
            apt.setNextDueAt(p.nextDueAt());
            apt.setNextDueMeter(p.nextDueMeter());
            apt.setNextDueMeterKind(p.nextDueMeterKind());
            apt.setRemainingMeter(p.remainingMeter());
            apt.setRemainingDays(p.remainingDays());
            apt.setStatus(p.status() != null ? p.status() : PlanTaskStatus.OK);
            ap.addTask(apt);
        }
        assetPlans.save(ap);
    }

    private MeterReading reading(AssetMeter meter, BigDecimal value, Instant at) {
        MeterReading r = new MeterReading();
        r.setMeter(meter);
        r.setValue(value);
        r.setReadingAt(at);
        r.setSource(MeterReadingSource.IMPORT);
        return r;
    }

    private void criticality(Asset asset, int production, int safety, int financial) {
        AssetCriticality c = new AssetCriticality();
        c.setAsset(asset);
        c.setProductionImpact(production);
        c.setSafetyImpact(safety);
        c.setFinancialImpact(financial);
        c.setOverall(AssetCriticality.computeOverall(production, safety, financial));
        c.setAssessedAt(Instant.now());
        criticalities.save(c);
    }
}
