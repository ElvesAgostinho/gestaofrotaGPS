package ao.autocare.modules.fuel;

import ao.autocare.domain.Asset;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
import ao.autocare.domain.enums.Enums.FuelSource;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.FuelRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * O que o sensor de combustível do GPS conta.
 *
 * <p>A cada posição com nível do depósito, compara-se com a anterior:
 * <ul>
 *   <li>subiu bastante com a viatura parada → <b>abastecimento detetado</b>:
 *       nasce um lançamento com origem SENSOR, com os litros que o sensor viu;</li>
 *   <li>desceu bastante com a viatura parada → <b>possível sangria</b>: uma
 *       anomalia, porque um depósito não perde 30 litros num parque;</li>
 *   <li>um abastecimento manual perto no tempo sem subida à altura →
 *       <b>o sensor não confirma</b>: o motorista declarou 80 L, o depósito
 *       subiu 30. É a regra que mais dinheiro recupera.</li>
 * </ul>
 *
 * <p>Os sensores são ruidosos — o combustível abana — por isso só se conta o
 * que acontece <b>parado</b> e acima de um limiar. Uma descida de 3 litros em
 * andamento é consumo e ondulação; uma descida de 30 litros parado é outra
 * coisa. Esta cautela deixa passar sangrias pequenas; preferível a acordar o
 * gestor com cinquenta alarmes falsos por dia até ele desligar tudo.
 */
@Component
public class FuelSensorWatch {

    private static final Logger log = LoggerFactory.getLogger(FuelSensorWatch.class);

    /** Subida mínima para contar como abastecimento, em litros. */
    static final BigDecimal SUBIDA_MINIMA_L = new BigDecimal("8");
    /** Descida mínima parado para contar como sangria, em litros. */
    static final BigDecimal DESCIDA_MINIMA_L = new BigDecimal("8");
    /** Parado: abaixo disto a viatura não anda, o combustível não abana. */
    static final BigDecimal PARADO_KMH = new BigDecimal("3");
    /** Janela para cruzar o sensor com um lançamento manual. */
    static final Duration JANELA_CRUZAMENTO = Duration.ofHours(3);
    /** Diferença tolerada entre os litros declarados e os do sensor. */
    static final BigDecimal TOLERANCIA_L = new BigDecimal("10");
    static final BigDecimal TOLERANCIA_FRACAO = new BigDecimal("0.20");

    private final FuelRecordRepository records;
    private final FuelAnomalyRepository anomalies;

    public FuelSensorWatch(FuelRecordRepository records, FuelAnomalyRepository anomalies) {
        this.records = records;
        this.anomalies = anomalies;
    }

    /**
     * Uma posição nova com nível de combustível, comparada com a anterior que
     * também tinha. Chamado de dentro da transação que grava a posição.
     */
    public void aoReceber(Asset asset, GpsPosition anterior, GpsPosition atual) {
        if (asset == null || atual.getFuelLevelLiters() == null) {
            return;
        }
        asset.setFuelLevelLiters(atual.getFuelLevelLiters());
        asset.setFuelLevelAt(atual.getRecordedAt());

        if (anterior == null || anterior.getFuelLevelLiters() == null) {
            return;
        }
        BigDecimal delta = atual.getFuelLevelLiters().subtract(anterior.getFuelLevelLiters());
        boolean parado = parado(anterior) && parado(atual);

        if (delta.compareTo(SUBIDA_MINIMA_L) >= 0 && parado) {
            abastecimentoDetetado(asset, anterior, atual, delta);
        } else if (delta.negate().compareTo(DESCIDA_MINIMA_L) >= 0 && parado) {
            sangriaDetetada(asset, anterior, atual, delta.negate());
        }
    }

    /**
     * Um lançamento manual acabado de gravar: há um do sensor perto no tempo?
     * Se há e os litros não batem, o sensor não confirma.
     */
    public void cruzarManual(FuelRecord manual) {
        if (manual.getSource() != FuelSource.MANUAL || manual.getAsset() == null) {
            return;
        }
        List<FuelRecord> doSensor = records.findByAssetIdAndSourceAndFilledAtBetween(
                manual.getAsset().getId(), FuelSource.SENSOR,
                manual.getFilledAt().minus(JANELA_CRUZAMENTO),
                manual.getFilledAt().plus(JANELA_CRUZAMENTO));
        for (FuelRecord s : doSensor) {
            cruzar(manual, s);
        }
    }

    // ==== O que se faz com cada caso =========================================

    private void abastecimentoDetetado(Asset asset, GpsPosition antes, GpsPosition depois, BigDecimal litros) {
        FuelRecord r = new FuelRecord();
        r.setOrganization(asset.getOrganization());
        r.setAsset(asset);
        r.setFilledAt(depois.getRecordedAt());
        r.setLiters(litros.setScale(1, RoundingMode.HALF_UP));
        r.setSource(FuelSource.SENSOR);
        r.setSensorPositionId(depois.getId());
        r.setSensorLevelBefore(antes.getFuelLevelLiters());
        r.setSensorLevelAfter(depois.getFuelLevelLiters());
        r.setLatitude(depois.getLatitude());
        r.setLongitude(depois.getLongitude());
        if (depois.getOdometerKm() != null) {
            r.setMeterValue(depois.getOdometerKm());
            r.setMeterKind(MeterKind.ODOMETER);
        }
        // Cheio quando o nível final chega perto da capacidade; senão não se sabe.
        BigDecimal deposito = asset.getTankCapacityLiters();
        r.setFullTank(deposito != null && depois.getFuelLevelLiters()
                .compareTo(deposito.multiply(new BigDecimal("0.93"))) >= 0);
        r.setNotes("Detetado pelo sensor do GPS: o depósito subiu de "
                + antes.getFuelLevelLiters().setScale(0, RoundingMode.HALF_UP) + " para "
                + depois.getFuelLevelLiters().setScale(0, RoundingMode.HALF_UP) + " L.");
        records.save(r);
        log.info("Abastecimento pelo sensor: {} +{} L", asset.getTag(), r.getLiters());

        // Já havia um lançamento manual desta hora? Cruza-se agora.
        List<FuelRecord> manuais = records.findByAssetIdAndSourceAndFilledAtBetween(
                asset.getId(), FuelSource.MANUAL,
                depois.getRecordedAt().minus(JANELA_CRUZAMENTO),
                depois.getRecordedAt().plus(JANELA_CRUZAMENTO));
        for (FuelRecord m : manuais) {
            cruzar(m, r);
        }
    }

    private void sangriaDetetada(Asset asset, GpsPosition antes, GpsPosition depois, BigDecimal litros) {
        // Uma sangria já assinalada na última hora não se repete a cada posição.
        if (anomalies.existsByAssetIdAndKindAndOccurredAtAfter(
                asset.getId(), FuelAnomalyKind.SENSOR_DRAIN,
                depois.getRecordedAt().minus(Duration.ofHours(1)))) {
            return;
        }
        FuelAnomaly a = new FuelAnomaly();
        a.setOrganization(asset.getOrganization());
        a.setAsset(asset);
        a.setKind(FuelAnomalyKind.SENSOR_DRAIN);
        a.setSeverity(litros.compareTo(new BigDecimal("30")) >= 0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING);
        a.setDetectedAt(Instant.now());
        a.setOccurredAt(depois.getRecordedAt());
        a.setExpectedValue(antes.getFuelLevelLiters());
        a.setObservedValue(depois.getFuelLevelLiters());
        a.setUnit("L");
        a.setLitersAtRisk(litros.setScale(1, RoundingMode.HALF_UP));
        a.setTitle("Depósito desceu " + litros.setScale(0, RoundingMode.HALF_UP) + " L com a viatura parada");
        a.setDetail("Entre " + antes.getRecordedAt() + " e " + depois.getRecordedAt()
                + " o sensor passou de " + antes.getFuelLevelLiters().setScale(0, RoundingMode.HALF_UP)
                + " para " + depois.getFuelLevelLiters().setScale(0, RoundingMode.HALF_UP)
                + " L sem a viatura se mexer. Um depósito parado não perde combustível sozinho: "
                + "verifique no local e com o motorista.");
        anomalies.save(a);
        log.info("Sangria pelo sensor: {} -{} L", asset.getTag(), litros);
    }

    /** O manual diz X, o sensor viu Y. Se a diferença passa a tolerância, anomalia no manual. */
    private void cruzar(FuelRecord manual, FuelRecord sensor) {
        if (manual.getLiters() == null || sensor.getLiters() == null) {
            return;
        }
        if (anomalies.existsByFuelRecordIdAndKind(manual.getId(), FuelAnomalyKind.SENSOR_MISMATCH)) {
            return;
        }
        BigDecimal diferenca = manual.getLiters().subtract(sensor.getLiters());
        BigDecimal tolerancia = TOLERANCIA_L.max(manual.getLiters().multiply(TOLERANCIA_FRACAO));
        if (diferenca.abs().compareTo(tolerancia) <= 0) {
            return;
        }
        FuelAnomaly a = new FuelAnomaly();
        a.setOrganization(manual.getOrganization());
        a.setAsset(manual.getAsset());
        a.setFuelRecord(manual);
        a.setDriver(manual.getDriver());
        a.setKind(FuelAnomalyKind.SENSOR_MISMATCH);
        a.setSeverity(diferenca.signum() > 0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING);
        a.setDetectedAt(Instant.now());
        a.setOccurredAt(manual.getFilledAt());
        a.setExpectedValue(sensor.getLiters());
        a.setObservedValue(manual.getLiters());
        a.setUnit("L");
        // Só há litros em risco quando se declarou MAIS do que entrou.
        BigDecimal emRisco = diferenca.signum() > 0 ? diferenca.setScale(1, RoundingMode.HALF_UP) : null;
        a.setLitersAtRisk(emRisco);
        a.setCurrency(manual.getCurrency());
        if (emRisco != null && manual.getPricePerLiter() != null) {
            a.setCostAtRisk(emRisco.multiply(manual.getPricePerLiter()).setScale(2, RoundingMode.HALF_UP));
        }
        a.setTitle("Declarados " + manual.getLiters().setScale(0, RoundingMode.HALF_UP)
                + " L, o sensor viu " + sensor.getLiters().setScale(0, RoundingMode.HALF_UP) + " L");
        a.setDetail("O lançamento diz " + manual.getLiters().setScale(1, RoundingMode.HALF_UP)
                + " L; o sensor do GPS registou uma subida de "
                + sensor.getLiters().setScale(1, RoundingMode.HALF_UP) + " L à mesma hora ("
                + sensor.getSensorLevelBefore().setScale(0, RoundingMode.HALF_UP) + " → "
                + sensor.getSensorLevelAfter().setScale(0, RoundingMode.HALF_UP) + " L). "
                + (diferenca.signum() > 0
                        ? "A diferença é combustível pago que não entrou no depósito."
                        : "Entrou mais do que o declarado: confira a fatura."));
        anomalies.save(a);
    }

    private static boolean parado(GpsPosition p) {
        if (Boolean.TRUE.equals(p.getMoving())) {
            return false;
        }
        return p.getSpeedKph() == null || p.getSpeedKph().compareTo(PARADO_KMH) <= 0;
    }
}
