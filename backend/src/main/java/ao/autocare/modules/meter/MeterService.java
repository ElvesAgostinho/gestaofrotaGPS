package ao.autocare.modules.meter;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.MeterReading;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.MeterReadingSource;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.meter.dto.MeterDtos.AddReadingRequest;
import ao.autocare.modules.meter.dto.MeterDtos.AddReadingResponse;
import ao.autocare.modules.meter.dto.MeterDtos.MeterView;
import ao.autocare.modules.meter.dto.MeterDtos.ReadingView;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.MeterReadingRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leituras de medidor (horímetro / hodómetro). Deteta inconsistências
 * (retrocesso, valores no futuro, aumento superior ao tempo decorrido) e mantém
 * o valor corrente e a média de utilização diária em cache no medidor.
 */
@Service
public class MeterService {

    /** Aumento máximo plausível por dia para um hodómetro (km/dia). */
    private static final BigDecimal MAX_KM_PER_DAY = new BigDecimal("2500");

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final MeterReadingRepository readings;
    private final UserRepository users;
    private final ao.autocare.modules.plan.AssetPlanService assetPlans;
    private final AuditService audit;

    public MeterService(
            AssetRepository assets,
            AssetMeterRepository meters,
            MeterReadingRepository readings,
            UserRepository users,
            ao.autocare.modules.plan.AssetPlanService assetPlans,
            AuditService audit) {
        this.assets = assets;
        this.meters = meters;
        this.readings = readings;
        this.users = users;
        this.assetPlans = assetPlans;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MeterView> listMeters(String orgId, String assetId) {
        requireAsset(orgId, assetId);
        return meters.findByAssetId(assetId).stream().map(MeterView::of).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReadingView> history(
            String orgId, String assetId, MeterKind kind, Pageable pageable) {
        requireAsset(orgId, assetId);
        AssetMeter meter = requireMeter(assetId, kind);
        return PagedResponse.of(
                readings.findByMeterIdOrderByReadingAtDesc(meter.getId(), pageable)
                        .map(ReadingView::of));
    }

    @Transactional
    public AddReadingResponse addReading(
            String orgId, String userId, String assetId, MeterKind kind, AddReadingRequest req) {

        requireAsset(orgId, assetId);
        AssetMeter meter = requireMeter(assetId, kind);

        Instant readingAt = req.readingAt() != null ? req.readingAt() : Instant.now();
        BigDecimal value = req.value();

        MeterReading previous = readings
                .findFirstByMeterIdOrderByReadingAtDescCreatedAtDesc(meter.getId())
                .orElse(null);

        MeterReading reading = new MeterReading();
        reading.setMeter(meter);
        reading.setValue(value);
        reading.setReadingAt(readingAt);
        reading.setSource(MeterReadingSource.MANUAL);
        reading.setRecordedBy(users.getReferenceById(userId));
        reading.setNote(req.note() != null && !req.note().isBlank() ? req.note().trim() : null);

        BigDecimal reference = previous != null ? previous.getValue() : meter.getCurrentValue();
        BigDecimal delta = value.subtract(reference);
        reading.setDelta(delta);

        String flag = detectInconsistency(kind, value, readingAt, previous, reference, delta);
        if (flag != null) {
            reading.setFlagged(true);
            reading.setFlagReason(flag);
        }
        readings.save(reading);

        // Atualiza o medidor apenas se esta for a leitura mais recente e não for um retrocesso.
        boolean isNewest = previous == null || !readingAt.isBefore(previous.getReadingAt());
        if (isNewest && value.compareTo(meter.getCurrentValue()) >= 0) {
            meter.setCurrentValue(value);
            meter.setLastReadingAt(readingAt);
        }
        meter.setDailyAverage(recomputeDailyAverage(meter.getId()));

        audit.record(orgId, userId, "meter.reading", "AssetMeter", meter.getId(),
                meter.getKind() + " = " + value + (flag != null ? " (inconsistência)" : ""));

        // A nova leitura pode ter aproximado ou vencido tarefas do plano de manutenção.
        assetPlans.recomputeForAsset(assetId);

        return new AddReadingResponse(MeterView.of(meter), ReadingView.of(reading));
    }

    /**
     * O contador que o aparelho de GPS traz alimenta o medidor do ativo.
     *
     * <p>É o «odómetro em tempo real»: a revisão dos 10 000 km vence quando
     * o camião faz 10 000 km, sem ninguém ir ler o painel. Nunca recua — um
     * aparelho trocado ou reiniciado manda valores mais baixos, e um medidor
     * que recuasse dava revisões para trás. E não grava uma leitura a cada
     * posição (seriam milhares por dia): só quando andou pelo menos 1 km ou
     * 0,1 h desde a última, ou passou uma hora.
     */
    @Transactional
    public void recordFromTelemetry(Asset asset, MeterKind kind, BigDecimal value, Instant at) {
        if (asset == null || value == null || value.signum() < 0) {
            return;
        }
        AssetMeter meter = meters.findByAssetIdAndKind(asset.getId(), kind).orElse(null);
        if (meter == null) {
            return;
        }
        BigDecimal atual = meter.getCurrentValue() != null ? meter.getCurrentValue() : BigDecimal.ZERO;
        BigDecimal subida = value.subtract(atual);
        if (subida.signum() <= 0) {
            return;
        }
        BigDecimal passoMinimo = kind == MeterKind.HOURMETER ? new BigDecimal("0.1") : BigDecimal.ONE;
        boolean passouUmaHora = meter.getLastReadingAt() == null
                || meter.getLastReadingAt().isBefore(at.minusSeconds(3600));
        if (subida.compareTo(passoMinimo) < 0 && !passouUmaHora) {
            return;
        }

        MeterReading reading = new MeterReading();
        reading.setMeter(meter);
        reading.setValue(value);
        reading.setReadingAt(at);
        reading.setSource(MeterReadingSource.TELEMETRY);
        reading.setDelta(subida);
        readings.save(reading);

        meter.setCurrentValue(value);
        meter.setLastReadingAt(at);
        meter.setDailyAverage(recomputeDailyAverage(meter.getId()));
        assetPlans.recomputeForAsset(asset.getId());
    }

    /**
     * A leitura escrita ao fechar uma ordem de manutenção («concluída aos
     * 1 245 h»). É uma leitura a sério — o mecânico olhou para o contador —
     * e por isso atualiza o contador do ativo, como uma leitura manual faria.
     * Sem isto, a ficha ficava com o valor antigo e o próximo intervalo de
     * manutenção contava a partir de um número que já não era verdade.
     * Um valor abaixo do contador atual não recua nada: fica só na ordem.
     */
    @Transactional
    public void recordFromWorkOrder(Asset asset, BigDecimal value, Instant at, String userId,
            String referencia) {
        if (asset == null || value == null || value.signum() < 0) {
            return;
        }
        AssetMeter meter = meters.findByAssetId(asset.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst()
                .orElse(meters.findByAssetId(asset.getId()).stream().findFirst().orElse(null));
        if (meter == null) {
            return;
        }
        BigDecimal atual = meter.getCurrentValue() != null ? meter.getCurrentValue() : BigDecimal.ZERO;
        if (value.compareTo(atual) <= 0) {
            return;
        }
        MeterReading reading = new MeterReading();
        reading.setMeter(meter);
        reading.setValue(value);
        reading.setReadingAt(at != null ? at : Instant.now());
        reading.setSource(MeterReadingSource.WORK_ORDER);
        reading.setDelta(value.subtract(atual));
        reading.setNote(referencia);
        if (userId != null) {
            reading.setRecordedBy(users.getReferenceById(userId));
        }
        readings.save(reading);

        meter.setCurrentValue(value);
        meter.setLastReadingAt(reading.getReadingAt());
        meter.setDailyAverage(recomputeDailyAverage(meter.getId()));
        assetPlans.recomputeForAsset(asset.getId());
    }

    // ------------------------------------------------------------------
    private String detectInconsistency(
            MeterKind kind, BigDecimal value, Instant readingAt,
            MeterReading previous, BigDecimal reference, BigDecimal delta) {

        if (readingAt.isAfter(Instant.now().plusSeconds(120))) {
            return "A data da leitura está no futuro.";
        }
        if (delta.signum() < 0) {
            return "O valor (" + value + ") é inferior à leitura anterior (" + reference + ").";
        }
        if (previous == null) {
            return null;
        }
        Duration elapsed = Duration.between(previous.getReadingAt(), readingAt);
        double elapsedHours = Math.max(elapsed.toMinutes() / 60.0, 0.0);

        if (kind == MeterKind.HOURMETER) {
            // Não é possível acumular mais horas de funcionamento do que o tempo real decorrido.
            if (elapsedHours > 0 && delta.doubleValue() > elapsedHours * 1.05 + 1) {
                return "O aumento de horas (" + delta + " h) é superior ao tempo decorrido desde "
                        + "a última leitura.";
            }
        } else {
            double elapsedDays = Math.max(elapsedHours / 24.0, 1.0 / 24.0);
            BigDecimal perDay = delta.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
            if (perDay.compareTo(MAX_KM_PER_DAY) > 0) {
                return "O aumento de quilómetros (" + perDay + " km/dia) parece demasiado elevado.";
            }
        }
        return null;
    }

    /** Média (unidade/dia) entre a primeira e a última das leituras recentes. */
    private BigDecimal recomputeDailyAverage(String meterId) {
        List<MeterReading> recent = readings.findTop12ByMeterIdOrderByReadingAtDescCreatedAtDesc(meterId);
        if (recent.size() < 2) {
            return null;
        }
        MeterReading newest = recent.get(0);
        MeterReading oldest = recent.get(recent.size() - 1);
        BigDecimal valueDelta = newest.getValue().subtract(oldest.getValue());
        if (valueDelta.signum() <= 0) {
            return null;
        }
        double days = Duration.between(oldest.getReadingAt(), newest.getReadingAt()).toMinutes() / 1440.0;
        if (days < 0.01) {
            return null;
        }
        return valueDelta.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private AssetMeter requireMeter(String assetId, MeterKind kind) {
        return meters.findByAssetIdAndKind(assetId, kind)
                .orElseThrow(() -> ApiException.notFound(
                        "Este ativo não tem um medidor do tipo " + kind + "."));
    }
}
