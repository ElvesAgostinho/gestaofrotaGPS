package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.FluidTopUp;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FluidTopUpRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os atestos de fluidos, e o que eles dizem sobre a viatura.
 *
 * <p>A ideia toda está numa frase: <b>fluido que se atesta é fluido que se
 * perdeu</b>. Num circuito fechado nada evapora. Quando alguém atesta água
 * todas as semanas, a viatura não está «a gastar água» — está a perder por
 * algum sítio, e o fim da linha é um motor gripado, uma junta de cabeça ou um
 * incêndio em viagem. É exactamente o que anda a acontecer aos autocarros de
 * carreira em Angola, e é uma coisa que o papel nunca apanha porque cada
 * atesto, sozinho, parece insignificante.
 *
 * <p>Por isso o sistema conta. Ao terceiro atesto de arrefecimento em trinta
 * dias — ou aos cinco litros — avisa quem gere, com o número em cima da mesa.
 * E no caso do líquido de travões avisa logo à primeira, porque esse não tem
 * desculpa nenhuma: ou há fuga, ou as pastilhas chegaram ao fim.
 */
@Service
public class FluidTopUpService {

    /** A janela em que se conta a tendência. */
    private static final Duration JANELA = Duration.ofDays(30);

    public record Registo(
            String id, String assetId, String assetTag, String kind, String kindLabel,
            BigDecimal liters, BigDecimal meterValue, String note,
            String recordedByLabel, Instant recordedAt) {

        static Registo of(FluidTopUp t) {
            return new Registo(t.getId(), t.getAsset().getId(), t.getAsset().getTag(),
                    t.getKind().name(), etiqueta(t.getKind()), t.getLiters(), t.getMeterValue(),
                    t.getNote(),
                    t.getRecordedBy() != null ? t.getRecordedBy().getName() : null,
                    t.getRecordedAt());
        }
    }

    /** O que o ecrã mostra depois de registar: o registo e o aviso, se houver. */
    public record Resultado(Registo record, String warning) {}

    private final FluidTopUpRepository topUps;
    private final AssetRepository assets;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public FluidTopUpService(FluidTopUpRepository topUps, AssetRepository assets,
            UserRepository users, NotificationService notifications, AuditService audit) {
        this.topUps = topUps;
        this.assets = assets;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    static String etiqueta(FluidTopUp.Kind k) {
        return switch (k) {
            case COOLANT -> "Água / líquido de arrefecimento";
            case ENGINE_OIL -> "Óleo do motor";
            case HYDRAULIC -> "Óleo hidráulico";
            case BRAKE -> "Líquido dos travões";
            case TRANSMISSION -> "Óleo da transmissão";
            case OTHER -> "Outro fluido";
        };
    }

    @Transactional(readOnly = true)
    public List<Registo> list(String orgId, String assetId) {
        return topUps.findByAssetIdOrderByRecordedAtDesc(assetId).stream()
                .filter(t -> t.getOrganization().getId().equals(orgId))
                .map(Registo::of)
                .toList();
    }

    @Transactional
    public Resultado record(String orgId, String userId, String assetId, FluidTopUp.Kind kind,
            BigDecimal liters, BigDecimal meterValue, String note, Instant when) {

        if (liters == null || liters.signum() <= 0) {
            throw ApiException.badRequest("Diga quantos litros atestou.");
        }
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Viatura não encontrada."));

        FluidTopUp t = new FluidTopUp();
        t.setOrganization(a.getOrganization());
        t.setAsset(a);
        t.setKind(kind == null ? FluidTopUp.Kind.COOLANT : kind);
        t.setLiters(liters);
        t.setMeterValue(meterValue);
        t.setNote(note != null && note.length() > 500 ? note.substring(0, 500) : note);
        t.setRecordedAt(when != null ? when : Instant.now());
        if (userId != null) {
            users.findById(userId).ifPresent(t::setRecordedBy);
        }
        topUps.save(t);

        audit.record(orgId, userId, "fluid.topup", "Asset", a.getId(),
                a.getTag() + " · " + etiqueta(t.getKind()) + " · " + liters + " L");

        String aviso = avaliar(a, t);
        return new Resultado(Registo.of(t), aviso);
    }

    /**
     * Olha para a tendência e avisa quem gere quando ela é má.
     *
     * <p>Devolve o mesmo texto ao motorista, para ele perceber porque é que
     * vale a pena registar: não é papelada, é o sistema a reparar por ele.
     */
    private String avaliar(Asset a, FluidTopUp novo) {
        Instant desde = Instant.now().minus(JANELA);
        List<FluidTopUp> recentes = topUps.findByAssetIdAndKindSince(a.getId(), novo.getKind(), desde);
        BigDecimal total = recentes.stream()
                .map(FluidTopUp::getLiters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int vezes = recentes.size();

        String texto = null;
        AlertSeverity gravidade = AlertSeverity.WARNING;

        if (novo.getKind() == FluidTopUp.Kind.BRAKE) {
            // Num circuito de travões fechado o líquido não desaparece.
            texto = "Foi atestado líquido de travões em " + a.getTag() + ". Num circuito fechado "
                    + "o líquido não desaparece: ou há fuga, ou as pastilhas chegaram ao fim. "
                    + "A viatura não deve sair antes de ser vista.";
            gravidade = AlertSeverity.CRITICAL;
        } else if (novo.getKind() == FluidTopUp.Kind.COOLANT
                && (vezes >= 3 || total.compareTo(new BigDecimal("5")) >= 0)) {
            texto = a.getTag() + " levou " + total.stripTrailingZeros().toPlainString()
                    + " L de água/líquido em " + vezes + " atesto(s) nos últimos 30 dias. "
                    + "Isto é uma fuga: verificar radiador, mangueiras, bomba de água e junta "
                    + "de cabeça antes que acabe em motor gripado — ou em incêndio.";
            gravidade = vezes >= 5 || total.compareTo(new BigDecimal("10")) >= 0
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        } else if (novo.getKind() == FluidTopUp.Kind.ENGINE_OIL
                && (vezes >= 3 || total.compareTo(new BigDecimal("4")) >= 0)) {
            texto = a.getTag() + " levou " + total.stripTrailingZeros().toPlainString()
                    + " L de óleo do motor em " + vezes + " atesto(s) nos últimos 30 dias. "
                    + "Um motor que come óleo está a queimá-lo ou a perdê-lo: análise de óleo "
                    + "e inspeção de fugas.";
        } else if (novo.getKind() == FluidTopUp.Kind.HYDRAULIC
                && (vezes >= 2 || total.compareTo(new BigDecimal("5")) >= 0)) {
            texto = a.getTag() + " levou " + total.stripTrailingZeros().toPlainString()
                    + " L de óleo hidráulico em 30 dias. Procurar fuga em mangueiras, "
                    + "cilindros e vedantes — e ter cuidado: fuga de alta pressão fere.";
        }

        if (texto != null) {
            notifications.notifyManagers(NotificationService.Draft.of(
                            a.getOrganization().getId(), AlertCategory.MAINTENANCE, gravidade,
                            "Perda de fluido: " + a.getTag(), texto,
                            "fluid_loss", a.getId() + ":" + novo.getKind().name(),
                            "/ativos/" + a.getId())
                    .forAsset(a));
        }
        return texto;
    }
}
