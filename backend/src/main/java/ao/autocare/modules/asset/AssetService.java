package ao.autocare.modules.asset;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetCriticality;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.AssetType;
import ao.autocare.domain.Location;
import ao.autocare.domain.MeterReading;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.asset.dto.AssetDtos;
import ao.autocare.modules.asset.dto.AssetDtos.AssetSummary;
import ao.autocare.modules.asset.dto.AssetDtos.AssetView;
import ao.autocare.modules.asset.dto.AssetDtos.CreateAssetRequest;
import ao.autocare.modules.asset.dto.AssetDtos.CriticalityRequest;
import ao.autocare.modules.asset.dto.AssetDtos.CriticalityView;
import ao.autocare.modules.asset.dto.AssetDtos.MeterView;
import ao.autocare.modules.asset.dto.AssetDtos.UpdateAssetRequest;
import ao.autocare.modules.asset.dto.AssetPhotoDtos.AssetPhotoView;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.AssetCriticalityRepository;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetPhotoRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.MeterReadingRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.storage.FileUrls;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final AssetTypeRepository assetTypes;
    private final LocationRepository locations;
    private final AssetMeterRepository meters;
    private final AssetCriticalityRepository criticalities;
    private final AssetPhotoRepository photos;
    private final MeterReadingRepository meterReadings;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final FileUrls fileUrls;
    private final AuditService audit;
    private final AssetPlanTaskRepository assetPlanTasks;

    public AssetService(
            AssetRepository assets,
            AssetTypeRepository assetTypes,
            LocationRepository locations,
            AssetMeterRepository meters,
            AssetCriticalityRepository criticalities,
            AssetPhotoRepository photos,
            MeterReadingRepository meterReadings,
            UserRepository users,
            OrganizationRepository organizations,
            FileUrls fileUrls,
            AuditService audit,
            AssetPlanTaskRepository assetPlanTasks) {
        this.assetPlanTasks = assetPlanTasks;
        this.assets = assets;
        this.assetTypes = assetTypes;
        this.locations = locations;
        this.meters = meters;
        this.criticalities = criticalities;
        this.photos = photos;
        this.meterReadings = meterReadings;
        this.users = users;
        this.organizations = organizations;
        this.fileUrls = fileUrls;
        this.audit = audit;
    }

    private List<AssetPhotoView> photoViews(String assetId) {
        return photos.findByAssetIdOrderByPrimaryDescSortOrderAscCreatedAtAsc(assetId).stream()
                .map(p -> AssetPhotoView.of(p, fileUrls::signed))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AssetSummary> list(String orgId, boolean archived, Pageable pageable) {
        return list(orgId, archived, null, pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AssetSummary> list(String orgId, boolean archived, String q, Pageable pageable) {
        if (q != null && !q.isBlank()) {
            String padrao = "%" + q.trim().toLowerCase() + "%";
            return PagedResponse.of(assets.search(orgId, archived, padrao, pageable).map(this::toSummary));
        }
        return PagedResponse.of(
                assets.findByOrganizationIdAndArchived(orgId, archived, pageable)
                        .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public AssetView get(String orgId, String id) {
        Asset a = load(orgId, id);
        return AssetView.of(a, meters.findByAssetId(id),
                criticalities.findByAssetId(id).orElse(null), photoViews(id));
    }

    @Transactional
    public AssetView create(String orgId, String userId, CreateAssetRequest req) {
        String tag = req.tag().trim();
        if (assets.existsByOrganizationIdAndTagIgnoreCase(orgId, tag)) {
            throw ApiException.conflict("Já existe um ativo com a etiqueta \"" + tag + "\".");
        }
        AssetType type = assetTypes.findByIdAndOrganizationId(req.assetTypeId(), orgId)
                .orElseThrow(() -> ApiException.badRequest("Tipo de ativo inválido."));

        Asset a = new Asset();
        a.setOrganization(organizations.getReferenceById(orgId));
        a.setAssetType(type);
        a.setTag(tag);
        a.setName(req.name().trim());
        applyCommon(a, orgId,
                req.locationId(), req.responsibleUserId(), req.manufacturer(), req.model(),
                req.serialNumber(), req.modelYear(), req.plate(), req.responsibleLabel(),
                req.acquisitionDate(), req.acquisitionValue(), req.currency(), req.photoUrl(),
                req.objective(), req.notes(), req.status());
        applyPosition(a, req.latitude(), req.longitude());
        applySpeedLimit(a, req.speedLimitKph());
        applyTankCapacity(a, req.tankCapacityLiters());
        if (req.downtimeCostPerHour() != null) {
            a.setDowntimeCostPerHour(req.downtimeCostPerHour().signum() > 0
                    ? req.downtimeCostPerHour() : null);
        }
        assets.save(a);

        createMeters(a, type, req.initialMeterValue());

        audit.record(orgId, userId, "asset.create", "Asset", a.getId(), a.getTag() + " · " + a.getName());
        return AssetView.of(a, meters.findByAssetId(a.getId()), null, java.util.List.of());
    }

    @Transactional
    public AssetView update(String orgId, String userId, String id, UpdateAssetRequest req) {
        Asset a = load(orgId, id);
        verificarVersao(req.version(), a.getVersion());
        if (req.tag() != null && !req.tag().isBlank()) {
            String tag = req.tag().trim();
            if (!tag.equalsIgnoreCase(a.getTag())
                    && assets.existsByOrganizationIdAndTagIgnoreCase(orgId, tag)) {
                throw ApiException.conflict("Já existe um ativo com a etiqueta \"" + tag + "\".");
            }
            a.setTag(tag);
        }
        if (req.name() != null && !req.name().isBlank()) a.setName(req.name().trim());
        if (req.assetTypeId() != null && !req.assetTypeId().isBlank()) {
            a.setAssetType(assetTypes.findByIdAndOrganizationId(req.assetTypeId(), orgId)
                    .orElseThrow(() -> ApiException.badRequest("Tipo de ativo inválido.")));
        }
        applyCommon(a, orgId,
                req.locationId(), req.responsibleUserId(), req.manufacturer(), req.model(),
                req.serialNumber(), req.modelYear(), req.plate(), req.responsibleLabel(),
                req.acquisitionDate(), req.acquisitionValue(), req.currency(), req.photoUrl(),
                req.objective(), req.notes(), req.status());
        applyPosition(a, req.latitude(), req.longitude());
        applySpeedLimit(a, req.speedLimitKph());
        applyTankCapacity(a, req.tankCapacityLiters());
        if (req.downtimeCostPerHour() != null) {
            a.setDowntimeCostPerHour(req.downtimeCostPerHour().signum() > 0
                    ? req.downtimeCostPerHour() : null);
        }

        audit.record(orgId, userId, "asset.update", "Asset", a.getId(), a.getTag());
        return AssetView.of(a, meters.findByAssetId(id),
                criticalities.findByAssetId(id).orElse(null), photoViews(id));
    }

    @Transactional
    public void archive(String orgId, String userId, String id, boolean archived) {
        Asset a = load(orgId, id);
        a.setArchived(archived);
        if (archived && a.getStatus() != AssetStatus.RETIRED) {
            a.setStatus(AssetStatus.RETIRED);
        }
        audit.record(orgId, userId, archived ? "asset.archive" : "asset.unarchive", "Asset", id, a.getTag());
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        Asset a = load(orgId, id);
        assets.delete(a);
        audit.record(orgId, userId, "asset.delete", "Asset", id, a.getTag());
    }

    // ---- Criticidade -------------------------------------------------
    @Transactional(readOnly = true)
    public CriticalityView getCriticality(String orgId, String id) {
        load(orgId, id);
        return CriticalityView.of(criticalities.findByAssetId(id).orElse(null));
    }

    @Transactional
    public CriticalityView setCriticality(String orgId, String userId, String id, CriticalityRequest req) {
        Asset a = load(orgId, id);
        AssetCriticality c = criticalities.findByAssetId(id).orElseGet(() -> {
            AssetCriticality fresh = new AssetCriticality();
            fresh.setAsset(a);
            return fresh;
        });
        c.setProductionImpact(req.productionImpact());
        c.setSafetyImpact(req.safetyImpact());
        c.setFinancialImpact(req.financialImpact());
        if (req.overall() != null) {
            c.setOverall(req.overall());
            c.setOverallManual(true);
        } else {
            c.setOverall(AssetCriticality.computeOverall(
                    req.productionImpact(), req.safetyImpact(), req.financialImpact()));
            c.setOverallManual(false);
        }
        c.setNotes(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : null);
        c.setAssessedAt(Instant.now());
        c.setAssessedBy(users.getReferenceById(userId));
        criticalities.save(c);

        audit.record(orgId, userId, "asset.criticality", "Asset", id,
                a.getTag() + " → " + c.getOverall());
        return CriticalityView.of(c);
    }

    // ------------------------------------------------------------------
    private void createMeters(Asset a, AssetType type, BigDecimal initialValue) {
        BigDecimal start = initialValue != null ? initialValue : BigDecimal.ZERO;
        AssetMeter primary = meters.save(newMeter(a, type.getPrimaryMeter(), true, start));
        if (start.signum() > 0) {
            baselineReading(primary, start);
        }
        if (type.getSecondaryMeter() != null && type.getSecondaryMeter() != type.getPrimaryMeter()) {
            meters.save(newMeter(a, type.getSecondaryMeter(), false, BigDecimal.ZERO));
        }
    }

    /**
     * Leitura inicial do medidor. Sem ela o histórico começaria vazio e as horas
     * de operação (usadas no MTBF) ficariam a zero até à segunda leitura.
     */
    private void baselineReading(AssetMeter meter, BigDecimal value) {
        MeterReading r = new MeterReading();
        r.setMeter(meter);
        r.setValue(value != null ? value : BigDecimal.ZERO);
        r.setReadingAt(Instant.now());
        r.setSource(ao.autocare.domain.enums.Enums.MeterReadingSource.MANUAL);
        r.setDelta(BigDecimal.ZERO);
        r.setNote("Leitura inicial no cadastro do ativo");
        meterReadings.save(r);
    }

    private AssetMeter newMeter(Asset a, MeterKind kind, boolean primary, BigDecimal value) {
        AssetMeter m = new AssetMeter();
        m.setAsset(a);
        m.setKind(kind);
        m.setUnit(kind == MeterKind.ODOMETER ? "km" : "h");
        m.setCurrentValue(value != null ? value : BigDecimal.ZERO);
        m.setPrimary(primary);
        if (value != null && value.signum() > 0) {
            m.setLastReadingAt(Instant.now());
        }
        return m;
    }

    private void applyCommon(
            Asset a, String orgId, String locationId, String responsibleUserId,
            String manufacturer, String model, String serialNumber, Integer modelYear,
            String plate, String responsibleLabel, Instant acquisitionDate,
            BigDecimal acquisitionValue, String currency, String photoUrl,
            String objective, String notes, AssetStatus status) {

        if (locationId != null) {
            a.setLocation(locationId.isBlank() ? null : resolveLocation(orgId, locationId));
        }
        if (responsibleUserId != null) {
            a.setResponsibleUser(responsibleUserId.isBlank() ? null : resolveUser(responsibleUserId));
        }
        if (manufacturer != null) a.setManufacturer(blankToNull(manufacturer));
        if (model != null) a.setModel(blankToNull(model));
        if (serialNumber != null) a.setSerialNumber(blankToNull(serialNumber));
        if (modelYear != null) a.setModelYear(modelYear);
        if (plate != null) a.setPlate(blankToNull(plate));
        if (responsibleLabel != null) a.setResponsibleLabel(blankToNull(responsibleLabel));
        if (acquisitionDate != null) a.setAcquisitionDate(acquisitionDate);
        if (acquisitionValue != null) a.setAcquisitionValue(acquisitionValue);
        if (currency != null && !currency.isBlank()) a.setCurrency(currency.trim().toUpperCase());
        if (photoUrl != null) a.setPhotoUrl(blankToNull(photoUrl));
        if (objective != null) a.setObjective(blankToNull(objective));
        if (notes != null) a.setNotes(blankToNull(notes));
        if (status != null) a.setStatus(status);
    }

    /** Zero ou negativo significa "sem limite próprio" e limpa o valor. */
    private void applySpeedLimit(Asset a, BigDecimal limit) {
        if (limit == null) return;
        if (limit.compareTo(BigDecimal.valueOf(400)) > 0) {
            throw ApiException.badRequest("O limite de velocidade indicado não é plausível.");
        }
        a.setSpeedLimitKph(limit.signum() > 0 ? limit : null);
    }

    /**
     * Capacidade do deposito, em litros.
     *
     * <p>Zero ou negativo apaga o valor em vez de o guardar: um deposito de
     * zero litros faria o controlo de combustivel assinalar todos os
     * abastecimentos como impossiveis.
     */
    private void applyTankCapacity(Asset a, java.math.BigDecimal litros) {
        if (litros == null) {
            return;
        }
        a.setTankCapacityLiters(litros.signum() > 0 ? litros : null);
    }

    private void applyPosition(Asset a, BigDecimal lat, BigDecimal lon) {
        if (lat == null && lon == null) return;
        if (lat == null || lon == null
                || lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || lon.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw ApiException.badRequest("Coordenadas inválidas.");
        }
        a.setLatitude(lat);
        a.setLongitude(lon);
        a.setPositionAt(java.time.Instant.now());
        a.setPositionSource(ao.autocare.domain.enums.Enums.PositionSource.MANUAL);
    }

    private Location resolveLocation(String orgId, String id) {
        return locations.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.badRequest("Local inválido."));
    }

    private User resolveUser(String id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Responsável inválido."));
    }

    private AssetSummary toSummary(Asset a) {
        List<MeterView> meterViews = new ArrayList<>();
        for (AssetMeter m : meters.findByAssetId(a.getId())) {
            meterViews.add(MeterView.of(m));
        }
        String crit = criticalities.findByAssetId(a.getId())
                .map(c -> c.getOverall().name()).orElse("LOW");
        String primaryPhoto = photos.findByAssetIdAndPrimaryTrue(a.getId()).stream()
                .findFirst().map(p -> fileUrls.signed(p.getFile().getId())).orElse(null);
        var familia = a.getAssetType().getCategory();
        AssetDtos.NextMaintenance proxima = assetPlanTasks.findByAssetPlanAssetId(a.getId()).stream()
                .filter(t -> t.getAssetPlan().isActive())
                .filter(t -> t.getRemainingMeter() != null || t.getRemainingDays() != null)
                .min(java.util.Comparator
                        .comparingInt(AssetDtos.NextMaintenance::urgencia)
                        .thenComparing(t -> t.getRemainingDays() != null
                                ? t.getRemainingDays() : Integer.MAX_VALUE)
                        .thenComparing(t -> t.getRemainingMeter() != null
                                ? t.getRemainingMeter() : new BigDecimal("1e12")))
                .map(AssetDtos.NextMaintenance::of)
                .orElse(null);
        return new AssetSummary(
                a.getId(), a.getTag(), a.getName(), a.getAssetType().getName(),
                familia.name(), familia.label(), familia.sortOrder(),
                a.getLocation() != null ? a.getLocation().getName() : null,
                a.getStatus().name(), crit, a.isArchived(),
                primaryPhoto, photos.countByAssetId(a.getId()),
                a.getLatitude(), a.getLongitude(), meterViews, proxima);
    }

    private Asset load(String orgId, String id) {
        return assets.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * A versão que o ecrã leu tem de ser a que está na base de dados.
     *
     * <p>Sem isto, o {@code @Version} só apanha colisões entre transações
     * simultâneas. O caso real — duas pessoas com a mesma ficha aberta durante
     * minutos — só se apanha comparando a versão que o ecrã devolve.
     */
    private static void verificarVersao(Long lida, long atual) {
        if (lida != null && lida != atual) {
            throw ApiException.conflict(
                    ao.autocare.common.GlobalExceptionHandler.MENSAGEM_VERSAO);
        }
    }
}
