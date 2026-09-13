package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverInfraction;
import ao.autocare.domain.DriverShift;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.fleet.dto.FleetDtos.InfractionView;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveInfractionRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveShiftRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.ShiftView;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverInfractionRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.DriverShiftRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O cadastro do motorista para lá da carta: infrações (com pontos e multas)
 * e a escala de serviço. E o aviso de que a carta, o cartão ou o exame
 * médico estão a caducar — antes de a viatura ficar parada na estrada por
 * causa de um papel.
 */
@Service
public class DriverRecordsService {

    /** Marcos de aviso, em dias: 30, 15, 7 e no dia; depois «caducado». */
    private static final int[] MARCOS = {30, 15, 7, 0};

    private final DriverRepository drivers;
    private final DriverInfractionRepository infractions;
    private final DriverShiftRepository shifts;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;

    public DriverRecordsService(DriverRepository drivers, DriverInfractionRepository infractions,
            DriverShiftRepository shifts, AssetRepository assets, OrganizationRepository organizations,
            UserRepository users, AuditService audit, NotificationService notifications) {
        this.drivers = drivers;
        this.infractions = infractions;
        this.shifts = shifts;
        this.assets = assets;
        this.organizations = organizations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ---- Infrações ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InfractionView> infractions(String orgId, String driverId, boolean showMoney) {
        requireDriver(orgId, driverId);
        return infractions.findByDriverIdOrderByOccurredAtDesc(driverId).stream()
                .map(i -> InfractionView.of(i, showMoney)).toList();
    }

    /** Pontos acumulados nos últimos 12 meses — a régua da política interna. */
    @Transactional(readOnly = true)
    public int pointsLastYear(String driverId) {
        return infractions.pointsSince(driverId, Instant.now().minus(365, ChronoUnit.DAYS));
    }

    @Transactional
    public InfractionView addInfraction(String orgId, String userId, String driverId,
            SaveInfractionRequest req, boolean showMoney) {
        Driver d = requireDriver(orgId, driverId);
        if (req.occurredAt() != null && req.occurredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.badRequest("A data da infração não pode ser no futuro.");
        }
        DriverInfraction i = new DriverInfraction();
        i.setOrganization(organizations.getReferenceById(orgId));
        i.setDriver(d);
        i.setKind(req.kind());
        i.setOccurredAt(req.occurredAt() != null ? req.occurredAt() : Instant.now());
        i.setDescription(blankToNull(req.description()));
        i.setPoints(req.points() != null ? Math.max(0, req.points()) : 0);
        i.setFineAmount(req.fineAmount() != null && req.fineAmount().signum() > 0 ? req.fineAmount() : null);
        i.setPaid(Boolean.TRUE.equals(req.paid()));
        i.setReference(blankToNull(req.reference()));
        if (req.assetId() != null && !req.assetId().isBlank()) {
            i.setAsset(requireAsset(orgId, req.assetId()));
        }
        if (userId != null) {
            i.setRecordedBy(users.getReferenceById(userId));
        }
        infractions.save(i);
        audit.record(orgId, userId, "driver.infraction", "Driver", driverId,
                d.getName() + " · " + req.kind().label() + (i.getPoints() > 0 ? " · " + i.getPoints() + " pontos" : ""));
        return InfractionView.of(i, showMoney);
    }

    @Transactional
    public InfractionView setInfractionPaid(String orgId, String userId, String id, boolean paid, boolean showMoney) {
        DriverInfraction i = infractions.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Infração não encontrada."));
        i.setPaid(paid);
        audit.record(orgId, userId, "driver.infraction.paid", "Driver", i.getDriver().getId(),
                paid ? "Multa paga" : "Multa por pagar");
        return InfractionView.of(i, showMoney);
    }

    @Transactional
    public void deleteInfraction(String orgId, String userId, String id) {
        DriverInfraction i = infractions.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Infração não encontrada."));
        infractions.delete(i);
        audit.record(orgId, userId, "driver.infraction.delete", "Driver", i.getDriver().getId(),
                i.getKind().label());
    }

    // ---- Escala ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ShiftView> roster(String orgId, Instant from, Instant to) {
        return shifts.inWindow(orgId, from, to).stream().map(ShiftView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftView> shiftsOf(String orgId, String driverId, Instant from, Instant to) {
        requireDriver(orgId, driverId);
        return shifts.forDriverInWindow(driverId, from, to).stream().map(ShiftView::of).toList();
    }

    @Transactional
    public ShiftView addShift(String orgId, String userId, String driverId, SaveShiftRequest req) {
        Driver d = requireDriver(orgId, driverId);
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw ApiException.badRequest("O fim do turno tem de ser depois do início.");
        }
        if (ChronoUnit.HOURS.between(req.startsAt(), req.endsAt()) > 24 * 14) {
            throw ApiException.badRequest("Um turno não pode durar mais de 14 dias. Para uma viagem longa, "
                    + "registe um turno por semana.");
        }
        // Um motorista não está em dois sítios ao mesmo tempo.
        List<DriverShift> sobrepostos = shifts.forDriverInWindow(driverId, req.startsAt(), req.endsAt());
        if (!sobrepostos.isEmpty()) {
            DriverShift s = sobrepostos.get(0);
            throw ApiException.conflict(d.getName() + " já tem turno nesse período ("
                    + s.getKind().label() + (s.getAsset() != null ? " · " + s.getAsset().getTag() : "") + ").");
        }
        DriverShift s = new DriverShift();
        s.setOrganization(organizations.getReferenceById(orgId));
        s.setDriver(d);
        s.setStartsAt(req.startsAt());
        s.setEndsAt(req.endsAt());
        s.setKind(req.kind() != null ? req.kind() : DriverShift.Kind.DAY);
        s.setNotes(blankToNull(req.notes()));
        if (req.assetId() != null && !req.assetId().isBlank()) {
            Asset a = requireAsset(orgId, req.assetId());
            // A viatura também não: dois motoristas escalados para o mesmo camião à mesma hora é erro.
            for (DriverShift outro : shifts.inWindow(orgId, req.startsAt(), req.endsAt())) {
                if (outro.getAsset() != null && outro.getAsset().getId().equals(a.getId())) {
                    throw ApiException.conflict(a.getTag() + " já está escalado para "
                            + outro.getDriver().getName() + " nesse período.");
                }
            }
            s.setAsset(a);
        }
        shifts.save(s);
        audit.record(orgId, userId, "driver.shift", "Driver", driverId,
                d.getName() + " · " + s.getKind().label() + (s.getAsset() != null ? " · " + s.getAsset().getTag() : ""));
        return ShiftView.of(s);
    }

    @Transactional
    public void deleteShift(String orgId, String userId, String id) {
        DriverShift s = shifts.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Turno não encontrado."));
        shifts.delete(s);
        audit.record(orgId, userId, "driver.shift.delete", "Driver", s.getDriver().getId(), s.getKind().label());
    }

    // ---- Avisos de validade ---------------------------------------------------

    /**
     * Avisa quem gere de cartas, cartões e exames médicos a caducar. Corre uma
     * vez por dia; cada marco (30, 15, 7, 0 dias, caducado) avisa uma vez.
     */
    @Transactional
    public int notifyExpiring() {
        int enviados = 0;
        for (Driver d : drivers.findAll()) {
            if (d.getStatus() == ao.autocare.domain.enums.Enums.DriverStatus.INACTIVE) {
                continue;
            }
            enviados += aviso(d, "Carta de condução", d.daysUntilLicenseExpiry(), "license");
            enviados += aviso(d, "Cartão de motorista", d.daysUntilCardExpiry(), "card");
            enviados += aviso(d, "Exame médico", d.daysUntilMedicalExpiry(), "medical");
        }
        return enviados;
    }

    private int aviso(Driver d, String nome, Long dias, String chave) {
        if (dias == null) {
            return 0;
        }
        Integer marco = marco(dias);
        if (marco == null) {
            return 0;
        }
        boolean caducado = dias < 0;
        String titulo = nome + (caducado ? " caducado — " : " a caducar — ") + d.getName();
        String corpo = caducado ? "Caducou há " + (-dias) + " dia(s). O motorista não pode conduzir."
                : "Caduca em " + dias + " dia(s).";
        return notifications.notifyManagers(NotificationService.Draft.of(
                d.getOrganization().getId(), AlertCategory.DOCUMENT,
                caducado ? AlertSeverity.CRITICAL : (dias <= 7 ? AlertSeverity.WARNING : AlertSeverity.INFO),
                titulo, corpo, "driver_" + chave + "_expiry", d.getId() + ":" + marco,
                "/motoristas"));
    }

    private static Integer marco(long dias) {
        if (dias < 0) {
            return -1;
        }
        for (int m : MARCOS) {
            if (dias <= m) {
                return m;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------

    private Driver requireDriver(String orgId, String id) {
        return drivers.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Motorista não encontrado."));
    }

    private Asset requireAsset(String orgId, String id) {
        return assets.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
