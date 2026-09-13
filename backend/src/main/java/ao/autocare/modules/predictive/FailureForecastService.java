package ao.autocare.modules.predictive;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.Failure;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.FailureRepository;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.OrganizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Previsão da próxima avaria por sistema (motor, travões, hidráulico…), a
 * partir do que a frota já mostrou: avarias repetidas no mesmo sistema, o
 * ritmo a que a viatura anda (km/h por dia, vindos do GPS ou das leituras) e
 * o consumo anómalo, que costuma anunciar problemas de motor e injeção.
 *
 * <p>Não é adivinhação: só há previsão quando há pelo menos duas avarias no
 * mesmo sistema, e a confiança é dita — «baixa» com duas, «alta» com quatro
 * ou mais a intervalos regulares. O objetivo é que o gestor abra a ordem
 * preventiva antes da viatura parar na estrada.
 */
@Service
public class FailureForecastService {

    private static final int JANELA_MESES = 24;
    private static final int AVISAR_DIAS = 14;

    private static final Map<String, String> SISTEMAS = Map.of(
            "ENGINE", "Motor", "HYDRAULIC", "Hidráulico", "FUEL", "Alimentação / injeção",
            "TRANSMISSION", "Transmissão", "AXLES", "Eixos e suspensão", "ELECTRICAL", "Elétrico",
            "BRAKES", "Travões", "STRUCTURE", "Estrutura / carroçaria", "OTHER", "Outro");

    public record Forecast(
            String assetId, String assetTag, String assetName,
            String systemCode, String systemLabel,
            int failures, Integer meanIntervalDays, BigDecimal meanIntervalMeter, String meterUnit,
            Instant lastFailureAt, BigDecimal lastFailureMeter,
            Instant predictedAt, BigDecimal predictedMeter,
            /** Dias até à data prevista; negativo = já devia ter acontecido. */
            Integer daysLeft,
            /** LOW, MEDIUM, HIGH. */
            String confidence,
            /** LOW, MEDIUM, HIGH: combina proximidade e confiança. */
            String risk,
            List<String> reasons,
            String suggestion) {}

    private final FailureRepository failures;
    private final AssetMeterRepository meters;
    private final FuelAnomalyRepository anomalies;
    private final OrganizationRepository organizations;
    private final NotificationService notifications;

    public FailureForecastService(FailureRepository failures, AssetMeterRepository meters,
            FuelAnomalyRepository anomalies, OrganizationRepository organizations, NotificationService notifications) {
        this.failures = failures;
        this.meters = meters;
        this.anomalies = anomalies;
        this.organizations = organizations;
        this.notifications = notifications;
    }

    public static String systemLabel(String code) {
        return code == null ? null : SISTEMAS.getOrDefault(code, code);
    }

    @Transactional(readOnly = true)
    public List<Forecast> forOrganization(String orgId) {
        Instant desde = Instant.now().minus(JANELA_MESES * 30L, ChronoUnit.DAYS);
        Map<String, Map<String, List<Failure>>> porAtivoESistema = new LinkedHashMap<>();
        for (Failure f : failures.forOrgSince(orgId, desde)) {
            if (f.getSystemCode() == null || f.getAsset() == null || f.getAsset().isArchived()) {
                continue;
            }
            porAtivoESistema.computeIfAbsent(f.getAsset().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(f.getSystemCode(), k -> new ArrayList<>()).add(f);
        }
        List<Forecast> out = new ArrayList<>();
        for (Map<String, List<Failure>> sistemas : porAtivoESistema.values()) {
            for (List<Failure> lista : sistemas.values()) {
                Forecast p = prever(lista);
                if (p != null) {
                    out.add(p);
                }
            }
        }
        out.sort(Comparator.comparing((Forecast p) -> p.daysLeft() == null ? Integer.MAX_VALUE : p.daysLeft()));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Forecast> forAsset(String orgId, String assetId) {
        return forOrganization(orgId).stream().filter(p -> p.assetId().equals(assetId)).toList();
    }

    private Forecast prever(List<Failure> lista) {
        if (lista.size() < 2) {
            return null;
        }
        lista.sort(Comparator.comparing(Failure::getDetectedAt));
        Asset a = lista.get(0).getAsset();
        Failure ultima = lista.get(lista.size() - 1);

        // Intervalos em dias e, quando há contador em ambas as avarias, em km/h.
        List<Long> dias = new ArrayList<>();
        List<BigDecimal> unidades = new ArrayList<>();
        for (int i = 1; i < lista.size(); i++) {
            dias.add(Math.max(1, Duration.between(lista.get(i - 1).getDetectedAt(), lista.get(i).getDetectedAt()).toDays()));
            BigDecimal m0 = lista.get(i - 1).getMeterValue();
            BigDecimal m1 = lista.get(i).getMeterValue();
            if (m0 != null && m1 != null && m1.compareTo(m0) > 0) {
                unidades.add(m1.subtract(m0));
            }
        }
        double mediaDias = dias.stream().mapToLong(Long::longValue).average().orElse(0);
        double desvio = Math.sqrt(dias.stream().mapToDouble(d -> (d - mediaDias) * (d - mediaDias)).average().orElse(0));
        double variacao = mediaDias > 0 ? desvio / mediaDias : 1;
        BigDecimal mediaUnidades = unidades.isEmpty() ? null
                : unidades.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(unidades.size()), 0, RoundingMode.HALF_UP);

        Instant previstaPorData = ultima.getDetectedAt().plus((long) Math.round(mediaDias), ChronoUnit.DAYS);
        Instant prevista = previstaPorData;
        BigDecimal previstoContador = null;
        String unidade = null;
        List<String> razoes = new ArrayList<>();
        razoes.add(lista.size() + " avarias no sistema em " + JANELA_MESES + " meses, em média a cada "
                + Math.round(mediaDias) + " dias");

        AssetMeter medidor = meters.findByAssetId(a.getId()).stream().filter(AssetMeter::isPrimary).findFirst()
                .orElse(meters.findByAssetId(a.getId()).stream().findFirst().orElse(null));
        if (mediaUnidades != null && ultima.getMeterValue() != null && medidor != null) {
            unidade = medidor.getUnit();
            previstoContador = ultima.getMeterValue().add(mediaUnidades);
            razoes.add("em média a cada " + mediaUnidades.toPlainString() + " " + unidade);
            // Com o ritmo diário (GPS ou leituras) converte-se o contador previsto em dias.
            if (medidor.getDailyAverage() != null && medidor.getDailyAverage().signum() > 0 && medidor.getCurrentValue() != null) {
                BigDecimal falta = previstoContador.subtract(medidor.getCurrentValue());
                long diasPorContador = falta.divide(medidor.getDailyAverage(), 0, RoundingMode.HALF_UP).longValue();
                Instant porContador = Instant.now().plus(Math.max(diasPorContador, -365), ChronoUnit.DAYS);
                razoes.add("ao ritmo atual de " + medidor.getDailyAverage().setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + " " + unidade + "/dia faltam " + Math.max(0, diasPorContador) + " dia(s) para o contador previsto");
                if (porContador.isBefore(prevista)) {
                    prevista = porContador;
                }
            }
        }

        // Consumo anómalo recente aponta para motor / injeção.
        boolean consumoAnomalo = false;
        if ("ENGINE".equals(ultima.getSystemCode()) || "FUEL".equals(ultima.getSystemCode())) {
            for (FuelAnomaly an : anomalies.findByAssetIdOrderByOccurredAtDesc(a.getId())) {
                if ((an.getKind() == FuelAnomalyKind.CONSUMPTION_SPIKE || an.getKind() == FuelAnomalyKind.SENSOR_DRAIN)
                        && an.getStatus() != AnomalyStatus.DISMISSED
                        && an.getOccurredAt().isAfter(Instant.now().minus(60, ChronoUnit.DAYS))) {
                    consumoAnomalo = true;
                    break;
                }
            }
            if (consumoAnomalo) {
                razoes.add("consumo anómalo nos últimos 60 dias: sintoma habitual de motor/injeção a degradar");
            }
        }

        int diasFaltam = (int) Duration.between(Instant.now(), prevista).toDays();
        String confianca = lista.size() >= 4 && variacao < 0.5 ? "HIGH" : lista.size() >= 3 ? "MEDIUM" : "LOW";
        String risco;
        if (diasFaltam <= AVISAR_DIAS && (!"LOW".equals(confianca) || consumoAnomalo)) {
            risco = "HIGH";
        } else if (diasFaltam <= 45 || consumoAnomalo) {
            risco = "MEDIUM";
        } else {
            risco = "LOW";
        }
        String sugestao = "Abrir uma ordem preventiva ao sistema «" + systemLabel(ultima.getSystemCode())
                + "» antes de " + ao.autocare.modules.org.PdfRenderer.data(prevista)
                + (ultima.getCause() != null ? " (última causa registada: " + ultima.getCause() + ")" : "")
                + ". Verificar a causa raiz: reparar outra vez trata o sintoma.";

        return new Forecast(a.getId(), a.getTag(), a.getName(), ultima.getSystemCode(), systemLabel(ultima.getSystemCode()),
                lista.size(), (int) Math.round(mediaDias), mediaUnidades, unidade,
                ultima.getDetectedAt(), ultima.getMeterValue(), prevista, previstoContador, diasFaltam,
                confianca, risco, razoes, sugestao);
    }

    /** Avisa quem gere das previsões a 14 dias (uma vez por ativo e sistema; repete só se a data mudar de mês). */
    @Transactional
    public int notifyImminent() {
        int enviados = 0;
        for (var org : organizations.findAll()) {
            for (Forecast p : forOrganization(org.getId())) {
                if (!"HIGH".equals(p.risk())) {
                    continue;
                }
                String origem = p.assetId() + ":" + p.systemCode() + ":" + p.predictedAt().truncatedTo(ChronoUnit.DAYS);
                enviados += notifications.notifyManagers(NotificationService.Draft.of(org.getId(),
                        AlertCategory.MAINTENANCE, AlertSeverity.WARNING,
                        "Avaria provável em " + p.daysLeft() + " dia(s) — " + p.assetTag(),
                        p.systemLabel() + ": " + String.join("; ", p.reasons()) + ". " + p.suggestion(),
                        "failure_forecast", origem, "/ativos/" + p.assetId() + "?tab=preditiva"));
            }
        }
        return enviados;
    }
}
