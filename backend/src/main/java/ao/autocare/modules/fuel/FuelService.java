package ao.autocare.modules.fuel;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.FuelSource;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.fuel.dto.FuelDtos.ConsumptionSummary;
import ao.autocare.modules.fuel.dto.FuelDtos.FuelRecordView;
import ao.autocare.modules.fuel.dto.FuelDtos.SaveFuelRecordRequest;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.modules.fleet.DriverService;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FuelRecordRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.storage.FileUrls;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abastecimentos e consumo.
 *
 * <p><b>Isto é contabilidade de combustível, não controlo.</b> Com registos
 * lançados à mão o sistema sabe quanto foi comprado e quanto rendeu, e compara
 * cada depósito com a média da própria viatura — o que expõe desvios ao fim de
 * alguns abastecimentos. O que <em>não</em> faz é detetar um furto no momento em
 * que acontece: para isso é preciso sensor de nível no depósito, com tabela de
 * calibração por viatura e filtro de ruído. O modelo de dados está preparado
 * para o receber ({@code FuelSource.SENSOR}, capacidade do depósito), mas
 * enquanto não existir o sistema diz o que sabe e não finge o resto.
 *
 * <p>O consumo só se calcula entre <b>enchimentos completos</b>: é a única
 * forma de saber exatamente quanto foi gasto num percurso. Um abastecimento
 * parcial fica registado para os custos, mas não produz consumo.
 */
@Service
public class FuelService {

    private final FuelSensorWatch sensorWatch;

    /** Desvio face à média da própria viatura a partir do qual se avisa. */
    private static final BigDecimal DEVIATION_THRESHOLD = new BigDecimal("0.20");

    /**
     * Abastecimentos completos necessários antes de a média valer alguma coisa.
     * Com dois ou três depósitos, a "média" é ruído e acusar alguém com base
     * nela seria injusto.
     */
    private static final int MIN_HISTORY = 4;

    private final FuelRecordRepository records;
    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final StoredFileRepository files;
    private final UserRepository users;
    private final FileUrls fileUrls;
    private final NotificationService notifications;
    private final AuditService audit;
    private final FuelControlService control;
    private final DriverService driverService;
    private final DriverRepository drivers;
    private final LocationRepository locations;

    public FuelService(
            FuelRecordRepository records,
            AssetRepository assets,
            AssetMeterRepository meters,
            StoredFileRepository files,
            UserRepository users,
            FileUrls fileUrls,
            NotificationService notifications,
            AuditService audit,
            FuelControlService control,
            DriverService driverService,
            DriverRepository drivers,
            LocationRepository locations,
            FuelSensorWatch sensorWatch) {
        this.sensorWatch = sensorWatch;
        this.records = records;
        this.assets = assets;
        this.meters = meters;
        this.files = files;
        this.users = users;
        this.fileUrls = fileUrls;
        this.notifications = notifications;
        this.audit = audit;
        this.control = control;
        this.driverService = driverService;
        this.drivers = drivers;
        this.locations = locations;
    }

    // ==== Registo =======================================================
    @Transactional
    public FuelRecordView record(
            String orgId, String userId, String assetId, SaveFuelRecordRequest req) {

        Asset asset = requireAsset(orgId, assetId);
        // Truncado ao milissegundo: a coluna guarda menos precisão do que um
        // Instant, e um valor que muda entre a memória e a base transforma
        // comparações de datas em resultados diferentes conforme o caminho.
        Instant filledAt = (req.filledAt() != null ? req.filledAt() : Instant.now())
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (filledAt.isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.badRequest("A data do abastecimento está no futuro.");
        }
        if (req.liters() == null || req.liters().signum() <= 0) {
            throw ApiException.badRequest("Indique quantos litros foram abastecidos.");
        }
        // Litros acima da capacidade do depósito NÃO são recusados. Recusar
        // ensina quem lança a baixar o número até o sistema aceitar — e é
        // exatamente a prova de que faltam litros que se perde nesse momento.
        // O registo entra como veio da fatura e o controlo levanta uma anomalia
        // crítica, com os litros e o dinheiro em excesso.

        FuelRecord r = new FuelRecord();
        r.setOrganization(asset.getOrganization());
        r.setAsset(asset);
        r.setFilledAt(filledAt);
        r.setLiters(req.liters());
        r.setPricePerLiter(req.pricePerLiter());
        r.setTotalCost(resolveCost(req));
        if (req.currency() != null && !req.currency().isBlank()) {
            r.setCurrency(req.currency().trim().toUpperCase());
        }
        r.setFullTank(req.fullTank() == null || req.fullTank());
        r.setStation(blankToNull(req.station()));
        r.setDriverLabel(blankToNull(req.driverLabel()));
        r.setPaymentMethod(blankToNull(req.paymentMethod()));
        r.setNotes(blankToNull(req.notes()));
        r.setSource(FuelSource.MANUAL);
        if (userId != null) {
            r.setCreatedBy(users.getReferenceById(userId));
        }
        if (req.receiptFileId() != null && !req.receiptFileId().isBlank()) {
            r.setReceipt(files.findById(req.receiptFileId())
                    .orElseThrow(() -> ApiException.badRequest("Ficheiro não encontrado.")));
        }

        AssetMeter primary = meters.findByAssetId(assetId).stream()
                .filter(AssetMeter::isPrimary).findFirst().orElse(null);
        r.setMeterKind(primary != null ? primary.getKind() : null);
        r.setMeterValue(req.meterValue());

        r.setLatitude(req.latitude());
        r.setLongitude(req.longitude());
        r.setCardNumber(blankToNull(req.cardNumber()));
        r.setInvoiceNumber(blankToNull(req.invoiceNumber()));
        r.setFuelType(req.fuelType());
        if (req.driverId() != null && !req.driverId().isBlank()) {
            r.setDriver(drivers.findByIdAndOrganizationId(req.driverId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Motorista não encontrado.")));
        } else {
            // Sem motorista indicado, imputa-se a quem estava atribuído ao ativo
            // naquele momento — e a ninguém se não havia atribuição.
            driverService.driverAt(assetId, filledAt).ifPresent(r::setDriver);
        }
        if (req.branchId() != null && !req.branchId().isBlank()) {
            r.setBranch(locations.findByIdAndOrganizationId(req.branchId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Filial não encontrada.")));
        } else if (asset.getLocation() != null) {
            r.setBranch(asset.getLocation());
        }

        computeConsumption(r, asset);
        records.save(r);

        // O controlo corre depois de gravar: precisa do id para não repetir a
        // mesma anomalia, e do registo em base para o cruzamento com o GPS.
        control.analyse(r);
        sensorWatch.cruzarManual(r);

        audit.record(orgId, userId, "fuel.record", "Asset", assetId,
                asset.getTag() + " · " + r.getLiters() + " L");
        return FuelRecordView.of(r, receiptUrl(r));
    }

    /**
     * Consumo desde o enchimento completo anterior.
     *
     * <p>Só se calcula quando <b>este</b> abastecimento enche o depósito e existe
     * um anterior também completo com leitura de medidor. Fora disso não há como
     * saber quanto do depósito foi realmente gasto no percurso.
     */
    private void computeConsumption(FuelRecord r, Asset asset) {
        if (!r.isFullTank() || r.getMeterValue() == null || r.getMeterKind() == null) {
            return;
        }
        FuelRecord previous = records
                .lastFullTankBefore(asset.getId(), r.getFilledAt(), r.getId())
                .orElse(null);
        if (previous == null || previous.getMeterValue() == null) {
            return;
        }
        BigDecimal travelled = r.getMeterValue().subtract(previous.getMeterValue());
        if (travelled.signum() <= 0) {
            // Medidor a recuar ou parado: o registo entra na mesma, mas sem
            // consumo — um número errado é pior do que nenhum.
            return;
        }
        r.setDistanceOrHours(travelled);

        if (r.getMeterKind() == MeterKind.ODOMETER) {
            // Litros por 100 km, a convenção usada em Angola e em Portugal.
            r.setConsumption(r.getLiters()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(travelled, 3, RoundingMode.HALF_UP));
            r.setConsumptionUnit("L/100km");
        } else {
            r.setConsumption(r.getLiters().divide(travelled, 3, RoundingMode.HALF_UP));
            r.setConsumptionUnit("L/h");
        }
    }

    // ==== Consulta ======================================================
    @Transactional(readOnly = true)
    public PagedResponse<FuelRecordView> history(String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(records.findByAssetIdOrderByFilledAtDesc(assetId, pageable)
                .map(r -> FuelRecordView.of(r, receiptUrl(r))));
    }

    /**
     * Resumo de consumo de um ativo, com o aviso de que isto é contabilidade e
     * não vigilância — o texto vai para o ecrã.
     */
    @Transactional(readOnly = true)
    public ConsumptionSummary summary(String orgId, String assetId) {
        Asset asset = requireAsset(orgId, assetId);
        List<FuelRecord> withConsumption = records.withConsumption(
                assetId, PageRequest.of(0, 24));

        BigDecimal average = average(withConsumption.stream()
                .map(FuelRecord::getConsumption).toList());
        BigDecimal best = withConsumption.stream().map(FuelRecord::getConsumption)
                .min(BigDecimal::compareTo).orElse(null);
        BigDecimal worst = withConsumption.stream().map(FuelRecord::getConsumption)
                .max(BigDecimal::compareTo).orElse(null);
        BigDecimal last = withConsumption.isEmpty()
                ? null : withConsumption.get(0).getConsumption();
        String unit = withConsumption.isEmpty()
                ? null : withConsumption.get(0).getConsumptionUnit();

        return new ConsumptionSummary(
                asset.getId(), asset.getTag(), unit,
                last, average, best, worst,
                withConsumption.size(),
                withConsumption.size() >= MIN_HISTORY,
                "Estes números vêm dos abastecimentos registados. O sistema faz "
                        + "contabilidade de combustível: mostra quanto foi comprado e "
                        + "quanto rendeu, e assinala desvios face à média deste ativo. "
                        + "Não deteta desvios em tempo real — isso exige um sensor de "
                        + "nível instalado no depósito.");
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        FuelRecord r = records.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Abastecimento não encontrado."));
        String label = r.getAsset().getTag() + " · " + r.getLiters() + " L";
        notifications.resolve("fuel_deviation", r.getId());
        records.delete(r);
        audit.record(orgId, userId, "fuel.delete", "FuelRecord", id, label);
    }

    // ==== Auxiliares ====================================================
    private BigDecimal resolveCost(SaveFuelRecordRequest req) {
        if (req.totalCost() != null) {
            return req.totalCost();
        }
        if (req.pricePerLiter() != null && req.liters() != null) {
            return req.pricePerLiter().multiply(req.liters()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), 3, RoundingMode.HALF_UP);
    }

    private String receiptUrl(FuelRecord r) {
        return r.getReceipt() != null ? fileUrls.signed(r.getReceipt().getId()) : null;
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
