package ao.autocare.modules.fuel;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetConsumptionBaseline;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.telemetry.Geo;
import ao.autocare.repo.AssetConsumptionBaselineRepository;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.FuelRecordRepository;
import ao.autocare.repo.GpsPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onde o combustível se perde.
 *
 * <p>A V17 deixou escrito que, com lançamentos à mão, o sistema faz
 * <b>contabilidade</b> de combustível e não <b>controlo</b>. Isto muda parte
 * disso. Cruzando o que foi declarado com o que o GPS viu aparecem coisas que a
 * contabilidade sozinha nunca mostra: um cartão usado a quarenta quilómetros da
 * viatura, litros a mais do que cabe no depósito, quilómetros declarados que
 * nunca foram percorridos.
 *
 * <p>O que continua a não ser possível sem sensor de nível: apanhar o furto
 * <b>no momento</b>. Aqui apanha-se depois — mas com números que aguentam uma
 * conversa difícil.
 *
 * <p><b>Nenhuma destas regras prova furto.</b> Um consumo alto tanto pode ser
 * mangueira na calha como filtro entupido, terreno pesado ou um horímetro mal
 * copiado. Por isso cada anomalia pede verificação e mostra as contas; acusar
 * é trabalho de quem investiga, não do software.
 */
@Service
public class FuelControlService {

    /** Amostras mínimas antes de haver base de consumo com que comparar. */
    static final int MIN_SAMPLES_FOR_BASELINE = 4;

    /** Desvio acima da base a partir do qual se assinala, quando não há dispersão. */
    static final BigDecimal SPIKE_TOLERANCE = new BigDecimal("0.25");

    /** Margem para arredondamento da bomba antes de dizer que não cabe no depósito. */
    static final BigDecimal TANK_TOLERANCE = new BigDecimal("1.02");

    /** Distância a partir da qual o abastecimento não foi onde a viatura estava. */
    static final double MAX_REFUEL_DISTANCE_M = 2_000;

    /** Janela para procurar a posição da viatura à volta do abastecimento. */
    static final Duration POSITION_WINDOW = Duration.ofMinutes(30);

    /** Dois abastecimentos do mesmo ativo dentro disto são suspeitos. */
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(30);

    /** Diferença aceite entre os km do medidor e os do GPS. */
    static final BigDecimal DISTANCE_TOLERANCE = new BigDecimal("0.15");

    /** Abaixo disto a comparação de distâncias não tem significado estatístico. */
    static final BigDecimal MIN_DISTANCE_TO_COMPARE_KM = new BigDecimal("20");

    /** Litros acima disto sem a viatura ter andado são de assinalar. */
    static final BigDecimal MIN_LITERS_WITHOUT_MOVEMENT = new BigDecimal("10");

    private final FuelRecordRepository records;
    private final FuelAnomalyRepository anomalies;
    private final AssetConsumptionBaselineRepository baselines;
    private final GpsPositionRepository positions;
    private final NotificationService notifications;

    public FuelControlService(
            FuelRecordRepository records,
            FuelAnomalyRepository anomalies,
            AssetConsumptionBaselineRepository baselines,
            GpsPositionRepository positions,
            NotificationService notifications) {
        this.records = records;
        this.anomalies = anomalies;
        this.baselines = baselines;
        this.positions = positions;
        this.notifications = notifications;
    }

    // ==== Ponto de entrada =================================================
    /**
     * Passa um abastecimento por todas as regras e guarda o que não bate certo.
     *
     * @return anomalias criadas agora
     */
    @Transactional
    public List<FuelAnomaly> analyse(FuelRecord r) {
        // As regras abaixo são para lançamentos de pessoas: preço, medidor,
        // duplicados. Um lançamento do sensor é a medição em si; o que se faz
        // com ele é cruzá-lo com os manuais, e isso vive no FuelSensorWatch.
        if (r.getSource() == ao.autocare.domain.enums.Enums.FuelSource.SENSOR) {
            return List.of();
        }
        Asset asset = r.getAsset();
        List<FuelAnomaly> criadas = new ArrayList<>();

        crossCheckWithGps(r);

        FuelRecord anterior = records.lastFullTankBefore(asset.getId(), r.getFilledAt(), r.getId())
                .orElse(null);

        volumeExceedsTank(r, asset).ifPresent(criadas::add);
        duplicateRefuel(r, asset).ifPresent(criadas::add);
        refuelAwayFromVehicle(r, asset).ifPresent(criadas::add);
        odometerRollback(r, asset, anterior).ifPresent(criadas::add);
        missingOdometer(r, asset).ifPresent(criadas::add);
        distanceMismatch(r, asset, anterior).ifPresent(criadas::add);
        refuelWithoutMovement(r, asset).ifPresent(criadas::add);

        // A base é recalculada depois das regras: usar uma base que já inclui o
        // registo suspeito faria o próprio desvio puxar a média para cima e, ao
        // fim de alguns abastecimentos, o furto passava a ser o normal.
        consumptionSpike(r, asset).ifPresent(criadas::add);
        recomputeBaseline(asset);

        criadas.forEach(this::notify);
        return criadas;
    }

    // ==== Regras ===========================================================
    /** Litros acima do que o depósito leva: ou a capacidade está errada, ou os litros. */
    private Optional<FuelAnomaly> volumeExceedsTank(FuelRecord r, Asset asset) {
        BigDecimal capacidade = asset.getTankCapacityLiters();
        if (capacidade == null || capacidade.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal limite = capacidade.multiply(TANK_TOLERANCE);
        if (r.getLiters().compareTo(limite) <= 0) {
            return Optional.empty();
        }
        BigDecimal excesso = r.getLiters().subtract(capacidade);
        return create(r, asset, FuelAnomalyKind.VOLUME_EXCEEDS_TANK, AlertSeverity.CRITICAL,
                capacidade, r.getLiters(), "L", excesso,
                "Abastecidos " + r.getLiters() + " L num depósito de " + capacidade + " L",
                "Foram lançados " + excesso.setScale(2, RoundingMode.HALF_UP)
                        + " L acima da capacidade do depósito. Ou a capacidade registada no "
                        + "ativo está errada, ou os litros faturados não entraram todos nesta "
                        + "viatura.");
    }

    /** Consumo muito acima do que este ativo costuma fazer. */
    private Optional<FuelAnomaly> consumptionSpike(FuelRecord r, Asset asset) {
        if (r.getConsumption() == null) {
            return Optional.empty();
        }
        AssetConsumptionBaseline base = baselines.findByAssetId(asset.getId()).orElse(null);
        if (base == null || base.getSampleCount() < MIN_SAMPLES_FOR_BASELINE) {
            return Optional.empty();
        }
        // Num ativo que sempre oscilou muito, 25% acima da média pode ser
        // perfeitamente normal. O limiar acompanha a dispersão do próprio ativo:
        // avisar por tudo ensina as pessoas a ignorar os avisos.
        BigDecimal margemFixa = base.getBaseline().multiply(SPIKE_TOLERANCE);
        BigDecimal margemDispersao = base.getStdDeviation() != null
                ? base.getStdDeviation().multiply(BigDecimal.valueOf(2)) : BigDecimal.ZERO;
        BigDecimal margem = margemFixa.max(margemDispersao);
        BigDecimal limite = base.getBaseline().add(margem);

        if (r.getConsumption().compareTo(limite) <= 0) {
            return Optional.empty();
        }
        BigDecimal excessoUnitario = r.getConsumption().subtract(base.getBaseline());
        BigDecimal litros = null;
        if (r.getDistanceOrHours() != null && r.getDistanceOrHours().signum() > 0) {
            litros = r.getMeterKind() == MeterKind.ODOMETER
                    ? excessoUnitario.multiply(r.getDistanceOrHours())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : excessoUnitario.multiply(r.getDistanceOrHours())
                            .setScale(2, RoundingMode.HALF_UP);
        }
        int percentagem = excessoUnitario
                .multiply(BigDecimal.valueOf(100))
                .divide(base.getBaseline(), 0, RoundingMode.HALF_UP).intValue();

        return create(r, asset, FuelAnomalyKind.CONSUMPTION_SPIKE, AlertSeverity.WARNING,
                base.getBaseline(), r.getConsumption(), r.getConsumptionUnit(), litros,
                "Consumo " + percentagem + "% acima da base de " + asset.getTag(),
                "Este abastecimento deu " + r.getConsumption() + " " + r.getConsumptionUnit()
                        + " contra uma base de " + base.getBaseline() + " apurada em "
                        + base.getSampleCount() + " depósitos. "
                        + (litros != null ? "São cerca de "
                                + litros.setScale(1, RoundingMode.HALF_UP)
                                + " L acima do esperado. " : "")
                        + "Pode ser furto, mas também manutenção em atraso, terreno pesado "
                        + "ou uma leitura mal copiada — vale a pena verificar.");
    }

    /** Quilómetros declarados que o GPS não viu. */
    private Optional<FuelAnomaly> distanceMismatch(
            FuelRecord r, Asset asset, FuelRecord anterior) {

        if (r.getMeterKind() != MeterKind.ODOMETER
                || r.getDistanceOrHours() == null
                || r.getGpsDistanceKm() == null
                || anterior == null) {
            return Optional.empty();
        }
        BigDecimal gps = r.getGpsDistanceKm();
        BigDecimal medidor = r.getDistanceOrHours();
        if (gps.compareTo(MIN_DISTANCE_TO_COMPARE_KM) < 0) {
            // Em percursos curtos, a soma do GPS tem erro relativo grande.
            return Optional.empty();
        }
        BigDecimal diferenca = medidor.subtract(gps).abs();
        BigDecimal desvio = diferenca.divide(gps, 4, RoundingMode.HALF_UP);
        if (desvio.compareTo(DISTANCE_TOLERANCE) <= 0) {
            return Optional.empty();
        }
        boolean medidorMaior = medidor.compareTo(gps) > 0;
        AssetConsumptionBaseline base = baselines.findByAssetId(asset.getId()).orElse(null);
        BigDecimal litros = base != null
                ? diferenca.multiply(base.getBaseline())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : null;

        return create(r, asset, FuelAnomalyKind.DISTANCE_MISMATCH, AlertSeverity.WARNING,
                gps, medidor, "km", litros,
                "Medidor e GPS discordam em " + diferenca.setScale(1, RoundingMode.HALF_UP)
                        + " km",
                "O medidor diz " + medidor.setScale(1, RoundingMode.HALF_UP)
                        + " km desde o último depósito cheio; o GPS registou "
                        + gps.setScale(1, RoundingMode.HALF_UP) + " km. "
                        + (medidorMaior
                                ? "Quilómetros declarados a mais fazem o consumo parecer "
                                        + "melhor do que é e escondem combustível em falta."
                                : "O aparelho pode ter estado sem sinal durante parte do "
                                        + "percurso — confirme antes de concluir."));
    }

    /** O cartão foi usado longe de onde a viatura estava. */
    private Optional<FuelAnomaly> refuelAwayFromVehicle(FuelRecord r, Asset asset) {
        if (!Boolean.FALSE.equals(r.getGpsVerified()) || r.getGpsDistanceM() == null) {
            return Optional.empty();
        }
        BigDecimal km = r.getGpsDistanceM()
                .divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return create(r, asset, FuelAnomalyKind.REFUEL_AWAY_FROM_VEHICLE, AlertSeverity.CRITICAL,
                BigDecimal.valueOf(MAX_REFUEL_DISTANCE_M / 1000), km, "km", r.getLiters(),
                "Abastecimento a " + km + " km de onde a viatura estava",
                "O abastecimento foi lançado num sítio onde esta viatura não estava. "
                        + "Os " + r.getLiters() + " L podem ter ido para outro depósito. "
                        + "Confirme o cartão e o talão.");
    }

    /** Dois abastecimentos quase ao mesmo tempo. */
    private Optional<FuelAnomaly> duplicateRefuel(FuelRecord r, Asset asset) {
        List<FuelRecord> proximos = records.betweenForAsset(
                asset.getId(),
                r.getFilledAt().minus(DUPLICATE_WINDOW),
                r.getFilledAt().plus(DUPLICATE_WINDOW));

        Optional<FuelRecord> gemeo = proximos.stream()
                .filter(outro -> !outro.getId().equals(r.getId()))
                // O sensor do GPS a detetar o mesmo abastecimento não é um
                // duplicado: é a confirmação dele. O cruzamento é outra regra.
                .filter(outro -> outro.getSource() != ao.autocare.domain.enums.Enums.FuelSource.SENSOR)
                .findFirst();
        if (gemeo.isEmpty()) {
            return Optional.empty();
        }
        FuelRecord outro = gemeo.get();
        BigDecimal menor = r.getLiters().min(outro.getLiters());
        return create(r, asset, FuelAnomalyKind.DUPLICATE_REFUEL, AlertSeverity.WARNING,
                null, BigDecimal.valueOf(2), "abastecimentos", menor,
                "Dois abastecimentos em menos de " + DUPLICATE_WINDOW.toMinutes() + " minutos",
                "Este ativo tem outro abastecimento de " + outro.getLiters() + " L registado "
                        + "quase à mesma hora. Pode ser lançamento em duplicado ou dupla "
                        + "cobrança — confirme os talões.");
    }

    /** Medidor a recuar: ou foi mal copiado, ou foi mexido. */
    private Optional<FuelAnomaly> odometerRollback(
            FuelRecord r, Asset asset, FuelRecord anterior) {

        if (r.getMeterValue() == null || anterior == null || anterior.getMeterValue() == null) {
            return Optional.empty();
        }
        if (r.getMeterValue().compareTo(anterior.getMeterValue()) >= 0) {
            return Optional.empty();
        }
        return create(r, asset, FuelAnomalyKind.ODOMETER_ROLLBACK, AlertSeverity.CRITICAL,
                anterior.getMeterValue(), r.getMeterValue(), "", null,
                "Medidor recuou de " + anterior.getMeterValue() + " para " + r.getMeterValue(),
                "A leitura registada é menor do que a do abastecimento anterior. Sem "
                        + "distância não há consumo que se calcule — e um medidor que recua "
                        + "costuma ser engano de quem copiou, mas nem sempre.");
    }

    /** Abasteceu sem o GPS ter visto a viatura andar. */
    private Optional<FuelAnomaly> refuelWithoutMovement(FuelRecord r, Asset asset) {
        if (r.getGpsDistanceKm() == null
                || r.getGpsDistanceKm().signum() > 0
                || r.getLiters().compareTo(MIN_LITERS_WITHOUT_MOVEMENT) < 0) {
            return Optional.empty();
        }
        return create(r, asset, FuelAnomalyKind.REFUEL_WITHOUT_MOVEMENT, AlertSeverity.WARNING,
                BigDecimal.ZERO, r.getLiters(), "L", r.getLiters(),
                "Abasteceu " + r.getLiters() + " L sem ter andado",
                "Desde o abastecimento anterior o GPS não registou deslocação nenhuma. "
                        + "Ou o aparelho esteve desligado, ou o combustível não foi para "
                        + "esta viatura.");
    }

    /** Sem leitura de medidor não há consumo — e sem consumo não há controlo. */
    private Optional<FuelAnomaly> missingOdometer(FuelRecord r, Asset asset) {
        if (r.getMeterValue() != null) {
            return Optional.empty();
        }
        return create(r, asset, FuelAnomalyKind.MISSING_ODOMETER, AlertSeverity.INFO,
                null, null, null, null,
                "Abastecimento sem leitura de medidor",
                "Sem quilómetros ou horas não há consumo que se calcule para este depósito. "
                        + "Não é irregularidade, mas é exatamente por aqui que o controlo de "
                        + "combustível deixa de funcionar numa frota.");
    }

    // ==== Cruzamento com o GPS =============================================
    /**
     * Confronta o abastecimento com o que o GPS viu.
     *
     * <p>Deixa {@code gpsVerified} a nulo quando não havia posição para
     * comparar. Nulo é "não foi possível verificar", e não "verificado e está
     * bem" — mostrar tudo a verde quando não se verificou nada é pior do que
     * não ter verificação nenhuma.
     */
    void crossCheckWithGps(FuelRecord r) {
        String assetId = r.getAsset().getId();

        // Distância percorrida desde o abastecimento cheio anterior.
        records.lastFullTankBefore(assetId, r.getFilledAt(), r.getId()).ifPresent(anterior ->
                r.setGpsDistanceKm(gpsDistanceBetween(
                        assetId, anterior.getFilledAt(), r.getFilledAt())));

        if (r.getLatitude() == null || r.getLongitude() == null) {
            return;
        }
        Optional<GpsPosition> perto = nearestPosition(assetId, r.getFilledAt());
        if (perto.isEmpty()) {
            return;
        }
        GpsPosition p = perto.get();
        if (!Geo.isValid(p.getLatitude(), p.getLongitude())) {
            return;
        }
        double metros = Geo.distanceMeters(
                r.getLatitude().doubleValue(), r.getLongitude().doubleValue(),
                p.getLatitude().doubleValue(), p.getLongitude().doubleValue());

        r.setGpsDistanceM(BigDecimal.valueOf(metros).setScale(2, RoundingMode.HALF_UP));
        r.setGpsVerified(metros <= MAX_REFUEL_DISTANCE_M);
        r.setGpsCheckedAt(Instant.now());
    }

    private Optional<GpsPosition> nearestPosition(String assetId, Instant momento) {
        List<GpsPosition> janela = positions.track(assetId,
                momento.minus(POSITION_WINDOW), momento.plus(POSITION_WINDOW));
        return janela.stream().min((a, b) -> Long.compare(
                Math.abs(Duration.between(a.getRecordedAt(), momento).getSeconds()),
                Math.abs(Duration.between(b.getRecordedAt(), momento).getSeconds())));
    }

    /** Soma dos troços entre duas datas, pelas posições gravadas. */
    private BigDecimal gpsDistanceBetween(String assetId, Instant de, Instant ate) {
        List<GpsPosition> pontos = positions.track(assetId, de, ate);
        if (pontos.size() < 2) {
            return null;
        }
        double metros = 0;
        for (int i = 1; i < pontos.size(); i++) {
            GpsPosition a = pontos.get(i - 1);
            GpsPosition b = pontos.get(i);
            if (Geo.isValid(a.getLatitude(), a.getLongitude())
                    && Geo.isValid(b.getLatitude(), b.getLongitude())) {
                metros += Geo.distanceMeters(
                        a.getLatitude().doubleValue(), a.getLongitude().doubleValue(),
                        b.getLatitude().doubleValue(), b.getLongitude().doubleValue());
            }
        }
        return BigDecimal.valueOf(metros / 1000).setScale(3, RoundingMode.HALF_UP);
    }

    // ==== Base de consumo ==================================================
    /**
     * Recalcula o consumo normal deste ativo a partir do seu próprio histórico.
     *
     * <p>Só compara o ativo consigo próprio. Um camião de obra gasta o dobro de
     * um ligeiro e isso não é anomalia nenhuma; listas de "maiores consumidores"
     * dizem a toda a gente o que já sabe e não revelam o que mudou.
     */
    @Transactional
    public Optional<AssetConsumptionBaseline> recomputeBaseline(Asset asset) {
        List<FuelRecord> historico = records.withConsumption(
                asset.getId(), PageRequest.of(0, 24));
        if (historico.size() < MIN_SAMPLES_FOR_BASELINE) {
            return Optional.empty();
        }
        List<BigDecimal> valores = historico.stream().map(FuelRecord::getConsumption).toList();

        BigDecimal soma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal media = soma.divide(
                BigDecimal.valueOf(valores.size()), 3, RoundingMode.HALF_UP);

        double variancia = valores.stream()
                .mapToDouble(v -> Math.pow(v.subtract(media).doubleValue(), 2))
                .sum() / valores.size();
        BigDecimal desvio = BigDecimal.valueOf(Math.sqrt(variancia))
                .setScale(3, RoundingMode.HALF_UP);

        AssetConsumptionBaseline base = baselines.findByAssetId(asset.getId())
                .orElseGet(AssetConsumptionBaseline::new);
        base.setOrganization(asset.getOrganization());
        base.setAsset(asset);
        base.setUnit(historico.get(0).getConsumptionUnit());
        base.setBaseline(media);
        base.setStdDeviation(desvio);
        base.setSampleCount(valores.size());
        base.setBest(valores.stream().min(BigDecimal::compareTo).orElse(null));
        base.setWorst(valores.stream().max(BigDecimal::compareTo).orElse(null));
        base.setFirstSampleAt(historico.get(historico.size() - 1).getFilledAt());
        base.setLastSampleAt(historico.get(0).getFilledAt());
        base.setComputedAt(Instant.now());
        return Optional.of(baselines.save(base));
    }

    // ==== Auxiliares =======================================================
    private Optional<FuelAnomaly> create(
            FuelRecord r, Asset asset, FuelAnomalyKind kind, AlertSeverity severity,
            BigDecimal esperado, BigDecimal observado, String unidade,
            BigDecimal litros, String titulo, String detalhe) {

        // A mesma regra não dispara duas vezes para o mesmo abastecimento.
        if (r.getId() != null && anomalies.existsByFuelRecordIdAndKind(r.getId(), kind)) {
            return Optional.empty();
        }
        FuelAnomaly a = new FuelAnomaly();
        a.setOrganization(asset.getOrganization());
        a.setAsset(asset);
        a.setFuelRecord(r);
        a.setDriver(r.getDriver());
        a.setKind(kind);
        a.setSeverity(severity);
        a.setDetectedAt(Instant.now());
        a.setOccurredAt(r.getFilledAt());
        a.setExpectedValue(esperado);
        a.setObservedValue(observado);
        a.setUnit(unidade);
        a.setLitersAtRisk(litros);
        a.setCurrency(r.getCurrency());
        a.setCostAtRisk(moneyFor(r, litros));
        a.setTitle(titulo);
        a.setDetail(detalhe);
        return Optional.of(anomalies.save(a));
    }

    /** Litros em risco convertidos ao preço praticado neste abastecimento. */
    private BigDecimal moneyFor(FuelRecord r, BigDecimal litros) {
        if (litros == null || litros.signum() <= 0) {
            return null;
        }
        BigDecimal preco = r.getPricePerLiter();
        if (preco == null && r.getTotalCost() != null && r.getLiters().signum() > 0) {
            preco = r.getTotalCost().divide(r.getLiters(), 4, RoundingMode.HALF_UP);
        }
        if (preco == null) {
            return null;
        }
        return litros.multiply(preco).setScale(2, RoundingMode.HALF_UP);
    }

    private void notify(FuelAnomaly a) {
        if (a.getSeverity() == AlertSeverity.INFO) {
            return; // qualidade de dados não precisa de acordar ninguém
        }
        String dinheiro = a.getCostAtRisk() != null
                ? " Em risco: " + a.getCostAtRisk() + " " + a.getCurrency() + "." : "";
        notifications.notifyManagers(NotificationService.Draft.of(
                        a.getOrganization().getId(),
                        AlertCategory.EXPENSE, a.getSeverity(),
                        a.getTitle() + " — " + a.getAsset().getTag(),
                        a.getDetail() + dinheiro,
                        "fuel_anomaly", a.getId(),
                        "/combustivel/anomalias/" + a.getId())
                .forAsset(a.getAsset()));
    }
}
