package ao.autocare.modules.tyre;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.Tyre;
import ao.autocare.domain.TyreReading;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.tyre.dto.TyreDtos.ReadingRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.ReadingView;
import ao.autocare.modules.tyre.dto.TyreDtos.RemoveRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.SaveTyreRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.TyreDetail;
import ao.autocare.modules.tyre.dto.TyreDtos.TyreView;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.TyreReadingRepository;
import ao.autocare.repo.TyreRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A vida de cada pneu: montar, medir, rodar, desmontar. O que sai daqui é o
 * custo por quilómetro por marca — a única forma de saber que pneu comprar.
 */
@Service
public class TyreService {

    /**
     * Avisa quem gere dos pneus com problema (sulco abaixo do mínimo, pressão
     * baixa) e dos que já têm mais de 6 anos montados — a borracha envelhece
     * mesmo sem andar. Um aviso por pneu enquanto o problema durar.
     */
    @org.springframework.transaction.annotation.Transactional
    public int notifyAlerts() {
        int enviados = 0;
        for (ao.autocare.domain.Tyre t : tyres.findAll()) {
            if (t.getStatus() != ao.autocare.domain.Tyre.Status.INSTALLED || t.getAsset() == null || t.getAsset().isArchived()) {
                notifications.resolve("tyre_alert", t.getId());
                continue;
            }
            String alerta = t.alerta();
            if (alerta == null && t.getInstalledAt() != null
                    && t.getInstalledAt().isBefore(java.time.Instant.now().minus(6L * 365, java.time.temporal.ChronoUnit.DAYS))) {
                alerta = "Montado há mais de 6 anos: a borracha envelhece mesmo sem rodar";
            }
            if (alerta == null) {
                notifications.resolve("tyre_alert", t.getId());
                continue;
            }
            enviados += notifications.notifyManagers(ao.autocare.modules.notification.NotificationService.Draft.of(
                    t.getOrganization().getId(),
                    ao.autocare.domain.enums.Enums.AlertCategory.TIRE,
                    ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                    "Pneu com alerta — " + t.getAsset().getTag() + " " + t.getPosition(),
                    alerta + ". Verifique e substitua antes de sair para a estrada.",
                    "tyre_alert", t.getId(), "/ativos/" + t.getAsset().getId() + "?tab=pneus").forAsset(t.getAsset()));
        }
        return enviados;
    }

    private final TyreRepository tyres;
    private final TyreReadingRepository readings;
    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuditService audit;
    private final ao.autocare.modules.notification.NotificationService notifications;

    public TyreService(TyreRepository tyres, TyreReadingRepository readings, AssetRepository assets,
            AssetMeterRepository meters, OrganizationRepository organizations, UserRepository users,
            AuditService audit,
            ao.autocare.modules.notification.NotificationService notifications) {
        this.tyres = tyres;
        this.readings = readings;
        this.assets = assets;
        this.meters = meters;
        this.organizations = organizations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<TyreView> listForAsset(String orgId, String assetId, boolean showMoney) {
        requireAsset(orgId, assetId);
        BigDecimal contador = contadorAtual(assetId);
        return tyres.findByAssetIdOrderByStatusAscPositionAsc(assetId).stream()
                .map(t -> TyreView.of(t, contador, showMoney)).toList();
    }

    /** Todos os pneus da empresa, montados primeiro — a página «Pneus». */
    @Transactional(readOnly = true)
    public List<TyreView> listAll(String orgId, boolean showMoney) {
        return tyres.findByOrganizationIdOrderByStatusAscUpdatedAtDesc(orgId).stream()
                .map(t -> TyreView.of(t, t.getAsset() != null ? contadorAtual(t.getAsset().getId()) : null,
                        showMoney))
                .toList();
    }

    @Transactional(readOnly = true)
    public TyreDetail get(String orgId, String id, boolean showMoney) {
        Tyre t = require(orgId, id);
        BigDecimal contador = t.getAsset() != null ? contadorAtual(t.getAsset().getId()) : null;
        return new TyreDetail(TyreView.of(t, contador, showMoney),
                readings.findByTyreIdOrderByMeasuredAtDesc(id).stream().map(ReadingView::of).toList());
    }

    @Transactional
    public TyreView install(String orgId, String userId, String assetId, SaveTyreRequest req,
            boolean showMoney) {
        Asset asset = requireAsset(orgId, assetId);
        String posicao = normalizarPosicao(req.position());
        if (posicao == null) {
            throw ApiException.badRequest("Indique a posição do pneu (ex.: FE, FD, TE1, TD1).");
        }
        tyres.findByAssetIdAndPositionAndStatus(assetId, posicao, Tyre.Status.INSTALLED).ifPresent(outro -> {
            throw ApiException.conflict("Já há um pneu montado na posição " + posicao
                    + " (" + descrever(outro) + "). Desmonte-o primeiro.");
        });
        BigDecimal contador = contadorAtual(assetId);

        Tyre t = new Tyre();
        t.setOrganization(organizations.getReferenceById(orgId));
        t.setAsset(asset);
        t.setPosition(posicao);
        t.setStatus(Tyre.Status.INSTALLED);
        t.setInstalledAt(req.installedAt() != null ? req.installedAt() : Instant.now());
        t.setInstalledMeter(req.installedMeter() != null ? req.installedMeter() : contador);
        aplicar(t, req);
        if (req.lastTreadMm() != null) {
            t.setLastTreadMm(req.lastTreadMm());
            t.setLastMeasuredAt(t.getInstalledAt());
        }
        tyres.save(t);

        audit.record(orgId, userId, "tyre.install", "Asset", assetId,
                asset.getTag() + " · " + posicao + " · " + descrever(t));
        return TyreView.of(t, contador, showMoney);
    }

    @Transactional
    public TyreView update(String orgId, String userId, String id, SaveTyreRequest req, boolean showMoney) {
        Tyre t = require(orgId, id);
        if (req.position() != null && !req.position().isBlank() && t.getAsset() != null) {
            String posicao = normalizarPosicao(req.position());
            if (!posicao.equals(t.getPosition())) {
                tyres.findByAssetIdAndPositionAndStatus(t.getAsset().getId(), posicao, Tyre.Status.INSTALLED)
                        .filter(o -> !o.getId().equals(id))
                        .ifPresent(o -> {
                            throw ApiException.conflict("A posição " + posicao + " já tem o pneu " + descrever(o) + ".");
                        });
                t.setPosition(posicao);
            }
        }
        if (req.installedAt() != null) t.setInstalledAt(req.installedAt());
        if (req.installedMeter() != null) t.setInstalledMeter(req.installedMeter());
        aplicar(t, req);
        audit.record(orgId, userId, "tyre.update", "Tyre", id, descrever(t));
        return TyreView.of(t, t.getAsset() != null ? contadorAtual(t.getAsset().getId()) : null, showMoney);
    }

    /** Medir: pressão e/ou sulco. Fica no histórico e atualiza a última medição. */
    @Transactional
    public TyreDetail addReading(String orgId, String userId, String id, ReadingRequest req, boolean showMoney) {
        Tyre t = require(orgId, id);
        if (req.pressure() == null && req.treadMm() == null) {
            throw ApiException.badRequest("Indique a pressão, o sulco, ou os dois.");
        }
        if (req.treadMm() != null && (req.treadMm().signum() < 0 || req.treadMm().compareTo(new BigDecimal("40")) > 0)) {
            throw ApiException.badRequest("O sulco é medido em milímetros (0 a 40).");
        }
        if (req.pressure() != null && (req.pressure().signum() < 0 || req.pressure().compareTo(new BigDecimal("20")) > 0)) {
            throw ApiException.badRequest("A pressão é medida em bar (0 a 20).");
        }
        TyreReading r = new TyreReading();
        r.setTyre(t);
        r.setMeasuredAt(req.measuredAt() != null ? req.measuredAt() : Instant.now());
        r.setMeterValue(req.meterValue() != null ? req.meterValue()
                : (t.getAsset() != null ? contadorAtual(t.getAsset().getId()) : null));
        r.setPressure(req.pressure());
        r.setTreadMm(req.treadMm());
        r.setNote(blankToNull(req.note()));
        if (userId != null) r.setRecordedBy(users.getReferenceById(userId));
        readings.save(r);

        if (t.getLastMeasuredAt() == null || !r.getMeasuredAt().isBefore(t.getLastMeasuredAt())) {
            if (req.pressure() != null) t.setLastPressure(req.pressure());
            if (req.treadMm() != null) t.setLastTreadMm(req.treadMm());
            t.setLastMeasuredAt(r.getMeasuredAt());
        }
        audit.record(orgId, userId, "tyre.reading", "Tyre", id,
                (req.pressure() != null ? req.pressure() + " bar " : "")
                        + (req.treadMm() != null ? req.treadMm() + " mm" : ""));
        return get(orgId, id, showMoney);
    }

    /** Desmontar. O custo por km fecha-se aqui: contador de saída − contador de entrada. */
    @Transactional
    public TyreView remove(String orgId, String userId, String id, RemoveRequest req, boolean showMoney) {
        Tyre t = require(orgId, id);
        if (t.getStatus() != Tyre.Status.INSTALLED) {
            throw ApiException.conflict("Este pneu já não está montado.");
        }
        BigDecimal contador = t.getAsset() != null ? contadorAtual(t.getAsset().getId()) : null;
        BigDecimal saida = req.removedMeter() != null ? req.removedMeter() : contador;
        if (saida != null && t.getInstalledMeter() != null && saida.compareTo(t.getInstalledMeter()) < 0) {
            throw ApiException.badRequest("O contador de saída (" + saida + ") é menor do que o da montagem ("
                    + t.getInstalledMeter() + ").");
        }
        t.setRemovedAt(req.removedAt() != null ? req.removedAt() : Instant.now());
        t.setRemovedMeter(saida);
        t.setRemovalReason(req.reason() != null ? req.reason() : Tyre.RemovalReason.OTHER);
        boolean fim = req.retire() == null || req.retire();
        t.setStatus(fim ? Tyre.Status.RETIRED : Tyre.Status.STOCK);
        String tag = t.getAsset() != null ? t.getAsset().getTag() : "";
        String posicao = t.getPosition();
        if (!fim) {
            // Em stock o pneu não tem posição; a viatura de origem fica na nota.
            t.setPosition(null);
        }
        if (req.note() != null && !req.note().isBlank()) {
            t.setNotes(blankToNull((t.getNotes() != null ? t.getNotes() + "\n" : "") + req.note().trim()));
        }
        BigDecimal custoKm = t.costPerUnit(contador);
        audit.record(orgId, userId, "tyre.remove", "Tyre", id,
                tag + " · " + posicao + " · " + t.getRemovalReason()
                        + (custoKm != null ? " · " + custoKm.stripTrailingZeros().toPlainString() + "/km" : ""));
        return TyreView.of(t, contador, showMoney);
    }

    /** Rotação: dois pneus da mesma viatura trocam de posição. Regista-se como medição. */
    @Transactional
    public List<TyreView> rotate(String orgId, String userId, String id, String otherId, boolean showMoney) {
        Tyre a = require(orgId, id);
        Tyre b = require(orgId, otherId);
        if (a.getAsset() == null || b.getAsset() == null || !a.getAsset().getId().equals(b.getAsset().getId())) {
            throw ApiException.badRequest("A rotação é entre dois pneus da mesma viatura.");
        }
        if (a.getStatus() != Tyre.Status.INSTALLED || b.getStatus() != Tyre.Status.INSTALLED) {
            throw ApiException.conflict("Os dois pneus têm de estar montados.");
        }
        String pa = a.getPosition();
        a.setPosition("__troca__");
        tyres.saveAndFlush(a);
        a.setPosition(b.getPosition());
        b.setPosition(pa);
        BigDecimal contador = contadorAtual(a.getAsset().getId());
        audit.record(orgId, userId, "tyre.rotate", "Asset", a.getAsset().getId(),
                a.getAsset().getTag() + " · " + pa + " ↔ " + a.getPosition());
        return List.of(TyreView.of(a, contador, showMoney), TyreView.of(b, contador, showMoney));
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        Tyre t = require(orgId, id);
        if (t.getStatus() == Tyre.Status.INSTALLED && t.getInstalledMeter() != null) {
            throw ApiException.conflict("Um pneu montado não se apaga: desmonte-o primeiro. "
                    + "Apagar é só para registos errados.");
        }
        tyres.delete(t);
        audit.record(orgId, userId, "tyre.delete", "Tyre", id, descrever(t));
    }

    // -----------------------------------------------------------------------

    private void aplicar(Tyre t, SaveTyreRequest req) {
        if (req.brand() != null) t.setBrand(blankToNull(req.brand()));
        if (req.model() != null) t.setModel(blankToNull(req.model()));
        if (req.size() != null) t.setSize(blankToNull(req.size()));
        if (req.serialNumber() != null) t.setSerialNumber(blankToNull(req.serialNumber()));
        if (req.cost() != null) t.setCost(req.cost().signum() < 0 ? null : req.cost());
        if (req.currency() != null && !req.currency().isBlank()) t.setCurrency(req.currency().trim().toUpperCase(Locale.ROOT));
        if (req.targetPressure() != null) t.setTargetPressure(req.targetPressure());
        if (req.minTreadMm() != null && req.minTreadMm().signum() > 0) t.setMinTreadMm(req.minTreadMm());
        if (req.notes() != null) t.setNotes(blankToNull(req.notes()));
    }

    private BigDecimal contadorAtual(String assetId) {
        return meters.findByAssetId(assetId).stream()
                .filter(AssetMeter::isPrimary).findFirst()
                .or(() -> meters.findByAssetId(assetId).stream().findFirst())
                .map(AssetMeter::getCurrentValue)
                .orElse(null);
    }

    private static String normalizarPosicao(String p) {
        if (p == null || p.isBlank()) return null;
        return p.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private static String descrever(Tyre t) {
        StringBuilder sb = new StringBuilder();
        if (t.getBrand() != null) sb.append(t.getBrand());
        if (t.getModel() != null) sb.append(sb.length() > 0 ? " " : "").append(t.getModel());
        if (t.getSize() != null) sb.append(sb.length() > 0 ? " " : "").append(t.getSize());
        if (t.getSerialNumber() != null) sb.append(sb.length() > 0 ? " · " : "").append(t.getSerialNumber());
        return sb.length() > 0 ? sb.toString() : "pneu";
    }

    private Tyre require(String orgId, String id) {
        return tyres.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Pneu não encontrado."));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
