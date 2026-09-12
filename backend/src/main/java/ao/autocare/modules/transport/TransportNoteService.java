package ao.autocare.modules.transport;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Driver;
import ao.autocare.domain.Location;
import ao.autocare.domain.TransportNote;
import ao.autocare.domain.TransportNoteItem;
import ao.autocare.domain.enums.Enums.TransportNoteStatus;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.transport.dto.TransportDtos.DeliverRequest;
import ao.autocare.modules.transport.dto.TransportDtos.ItemRequest;
import ao.autocare.modules.transport.dto.TransportDtos.NoteSummary;
import ao.autocare.modules.transport.dto.TransportDtos.NoteView;
import ao.autocare.modules.transport.dto.TransportDtos.SaveNoteRequest;
import ao.autocare.modules.workorder.CounterService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.TransportNoteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guias de transporte.
 *
 * <p>O percurso é o de um documento com valor fora da empresa: rascunho →
 * emitida → em trânsito → entregue. A partir de emitida deixa de se poder
 * mexer na carga, porque a guia já saiu em papel com o condutor — alterá-la
 * depois faria o documento na estrada e o do sistema dizerem coisas
 * diferentes.
 */
@Service
public class TransportNoteService {

    private static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");
    private static final String CONTADOR = "TRANSPORT_NOTE";

    private final TransportNoteRepository notes;
    private final OrganizationRepository organizations;
    private final AssetRepository assets;
    private final DriverRepository drivers;
    private final LocationRepository locations;
    private final CounterService counters;
    private final AuditService audit;

    public TransportNoteService(
            TransportNoteRepository notes,
            OrganizationRepository organizations,
            AssetRepository assets,
            DriverRepository drivers,
            LocationRepository locations,
            CounterService counters,
            AuditService audit) {
        this.notes = notes;
        this.organizations = organizations;
        this.assets = assets;
        this.drivers = drivers;
        this.locations = locations;
        this.counters = counters;
        this.audit = audit;
    }

    // ==== Consulta =========================================================

    @Transactional(readOnly = true)
    public Page<NoteSummary> list(String orgId, String status, Pageable pageable) {
        Page<TransportNote> pagina = (status != null && !status.isBlank())
                ? notes.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        orgId, parse(status), pageable)
                : notes.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable);
        // Convertida aqui e não no controlador, pela mesma razão do get():
        // a linha da lista lê a etiqueta da viatura e o nome do motorista.
        return pagina.map(NoteSummary::of);
    }

    @Transactional(readOnly = true)
    public NoteView get(String orgId, String id) {
        TransportNote n = require(orgId, id);
        // A vista é montada aqui dentro, com a sessão ainda aberta. Montada no
        // controlador, as relações preguiçosas (viatura, motorista, linhas da
        // carga) rebentam com LazyInitializationException.
        return NoteView.of(n);
    }

    // ==== Criação e edição =================================================

    @Transactional
    public NoteView create(String orgId, String userId, SaveNoteRequest req) {
        TransportNote n = new TransportNote();
        n.setOrganization(organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada.")));

        int ano = ZonedDateTime.now(LUANDA).getYear();
        n.setNoteYear(ano);
        n.setNumber(String.format("GT-%d-%06d", ano,
                counters.next(orgId, CONTADOR + ":" + ano)));
        n.setCreatedBy(userId);

        aplicar(orgId, n, req);
        substituirLinhas(n, req.items());
        n.recalculate();

        notes.save(n);
        notes.flush();

        audit.record(orgId, userId, "transport_note.create", "TransportNote", n.getId(),
                n.getNumber() + " · " + n.getOriginLabel() + " → " + n.getDestinationLabel());
        return NoteView.of(n);
    }

    @Transactional
    public NoteView update(String orgId, String userId, String id, SaveNoteRequest req) {
        TransportNote n = require(orgId, id);
        recusarSeEmitida(n);
        aplicar(orgId, n, req);
        substituirLinhas(n, req.items());
        n.recalculate();
        notes.flush();

        audit.record(orgId, userId, "transport_note.update", "TransportNote", n.getId(),
                n.getNumber());
        return NoteView.of(n);
    }

    // ==== Percurso =========================================================

    /**
     * Emite a guia: a partir daqui é um documento, não um rascunho.
     *
     * <p>Recusa emitir sem viatura, sem motorista e sem carga. Uma guia em
     * branco na estrada não serve para nada a ninguém.
     */
    @Transactional
    public NoteView issue(String orgId, String userId, String id) {
        TransportNote n = require(orgId, id);
        if (n.getStatus() != TransportNoteStatus.DRAFT) {
            throw ApiException.conflict("Esta guia já foi emitida.");
        }
        if (n.getAsset() == null) {
            throw ApiException.badRequest("Indique a viatura que vai transportar a carga.");
        }
        if (n.getDriver() == null && (n.getDriverLabel() == null || n.getDriverLabel().isBlank())) {
            throw ApiException.badRequest("Indique quem conduz.");
        }
        if (n.getItems().isEmpty() && (n.getCargoDescription() == null
                || n.getCargoDescription().isBlank())) {
            throw ApiException.badRequest("Uma guia sem carga não serve de documento.");
        }

        n.setStatus(TransportNoteStatus.ISSUED);
        n.setIssuedAt(Instant.now());
        audit.record(orgId, userId, "transport_note.issue", "TransportNote", n.getId(),
                n.getNumber() + " emitida");
        return NoteView.of(n);
    }

    @Transactional
    public NoteView depart(String orgId, String userId, String id, BigDecimal meter) {
        TransportNote n = require(orgId, id);
        if (n.getStatus() != TransportNoteStatus.ISSUED) {
            throw ApiException.conflict(
                    "Só uma guia emitida pode sair. Esta está " + n.getStatus().label().toLowerCase() + ".");
        }
        n.setStatus(TransportNoteStatus.IN_TRANSIT);
        n.setDepartedAt(Instant.now());
        n.setDepartureMeter(meter);
        audit.record(orgId, userId, "transport_note.depart", "TransportNote", n.getId(),
                n.getNumber() + " saiu de " + n.getOriginLabel());
        return NoteView.of(n);
    }

    /**
     * Entrega.
     *
     * <p>Quem recebe com reservas tem de dizer quais: é isso que vale quando a
     * discussão aparecer, semanas depois.
     */
    @Transactional
    public NoteView deliver(String orgId, String userId, String id, DeliverRequest req) {
        TransportNote n = require(orgId, id);
        if (n.getStatus() != TransportNoteStatus.IN_TRANSIT
                && n.getStatus() != TransportNoteStatus.ISSUED) {
            throw ApiException.conflict("Esta guia não está a caminho.");
        }
        if (req.receivedByName() == null || req.receivedByName().isBlank()) {
            throw ApiException.badRequest("Indique quem recebeu a carga.");
        }
        boolean aceite = req.accepted() == null || req.accepted();
        if (!aceite && (req.notes() == null || req.notes().isBlank())) {
            throw ApiException.badRequest(
                    "Se o cliente recebeu com reservas, escreva quais. É isso que vale mais tarde.");
        }

        n.setStatus(TransportNoteStatus.DELIVERED);
        n.setDeliveredAt(Instant.now());
        n.setReceivedByName(req.receivedByName().trim());
        n.setReceivedByDocument(vazioParaNulo(req.receivedByDocument()));
        n.setDeliveryAccepted(aceite);
        n.setDeliveryNotes(vazioParaNulo(req.notes()));
        n.setArrivalMeter(req.arrivalMeter());

        // A distância vem dos medidores, não do que alguém escreveu.
        if (n.getDepartureMeter() != null && req.arrivalMeter() != null) {
            BigDecimal d = req.arrivalMeter().subtract(n.getDepartureMeter());
            if (d.signum() < 0) {
                throw ApiException.badRequest(
                        "A leitura de chegada é menor do que a de partida.");
            }
            n.setDistanceKm(d);
        }

        audit.record(orgId, userId, "transport_note.deliver", "TransportNote", n.getId(),
                n.getNumber() + " entregue a " + n.getReceivedByName()
                        + (aceite ? "" : " (com reservas)"));
        return NoteView.of(n);
    }

    /**
     * Anula a guia.
     *
     * <p>Não se apaga: uma guia emitida circulou em papel, e o registo de que
     * existiu e foi anulada é o que explica o salto na numeração.
     */
    @Transactional
    public NoteView cancel(String orgId, String userId, String id, String reason) {
        TransportNote n = require(orgId, id);
        if (n.getStatus() == TransportNoteStatus.DELIVERED) {
            throw ApiException.conflict("Uma guia já entregue não se anula.");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw ApiException.badRequest("Explique porque é que a guia foi anulada. Fica registado.");
        }
        n.setStatus(TransportNoteStatus.CANCELLED);
        n.setCancellationReason(reason.trim());
        audit.record(orgId, userId, "transport_note.cancel", "TransportNote", n.getId(),
                n.getNumber() + " anulada: " + reason.trim());
        return NoteView.of(n);
    }

    // ==== Auxiliares =======================================================

    private void aplicar(String orgId, TransportNote n, SaveNoteRequest req) {
        n.setOriginLabel(obrigatorio(req.originLabel(), "Indique de onde parte a carga."));
        n.setDestinationLabel(obrigatorio(req.destinationLabel(), "Indique para onde vai."));
        n.setOriginAddress(vazioParaNulo(req.originAddress()));
        n.setDestinationAddress(vazioParaNulo(req.destinationAddress()));
        n.setTrailerPlate(vazioParaNulo(req.trailerPlate()));
        n.setDriverLabel(vazioParaNulo(req.driverLabel()));
        n.setDriverLicense(vazioParaNulo(req.driverLicense()));
        n.setCustomerName(vazioParaNulo(req.customerName()));
        n.setCustomerTaxId(vazioParaNulo(req.customerTaxId()));
        n.setCustomerContact(vazioParaNulo(req.customerContact()));
        n.setCargoDescription(vazioParaNulo(req.cargoDescription()));
        n.setHazardClass(vazioParaNulo(req.hazardClass()));
        n.setNotes(vazioParaNulo(req.notes()));
        n.setRouteId(vazioParaNulo(req.routeId()));

        if (req.assetId() != null && !req.assetId().isBlank()) {
            Asset a = assets.findByIdAndOrganizationId(req.assetId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Viatura não encontrada."));
            n.setAsset(a);
        } else {
            n.setAsset(null);
        }

        if (req.driverId() != null && !req.driverId().isBlank()) {
            Driver d = drivers.findByIdAndOrganizationId(req.driverId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Motorista não encontrado."));
            n.setDriver(d);
            if (n.getDriverLabel() == null) {
                n.setDriverLabel(d.getName());
            }
            if (n.getDriverLicense() == null) {
                n.setDriverLicense(d.getLicenseNumber());
            }
        } else {
            n.setDriver(null);
        }

        if (req.originBranchId() != null && !req.originBranchId().isBlank()) {
            Location l = locations.findByIdAndOrganizationId(req.originBranchId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Filial não encontrada."));
            n.setOriginBranch(l);
        } else {
            n.setOriginBranch(null);
        }
    }

    private void substituirLinhas(TransportNote n, List<ItemRequest> pedidas) {
        n.getItems().clear();
        if (pedidas == null) {
            return;
        }
        int ordem = 0;
        for (ItemRequest r : pedidas) {
            if (r.description() == null || r.description().isBlank()) {
                throw ApiException.badRequest("Cada linha da carga precisa de descrição.");
            }
            TransportNoteItem i = new TransportNoteItem();
            i.setTransportNote(n);
            i.setDescription(r.description().trim());
            i.setReference(vazioParaNulo(r.reference()));
            i.setQuantity(r.quantity());
            if (r.unit() != null && !r.unit().isBlank()) {
                i.setUnit(r.unit().trim());
            }
            i.setWeightKg(r.weightKg());
            i.setVolumeM3(r.volumeM3());
            i.setPackages(r.packages());
            i.setNotes(vazioParaNulo(r.notes()));
            i.setSortOrder(ordem++);
            n.getItems().add(i);
        }
    }

    /**
     * A carga de uma guia emitida não se altera.
     *
     * <p>O papel já saiu com o condutor; mudá-la aqui faria os dois documentos
     * dizerem coisas diferentes — e o que conta na estrada é o papel.
     */
    private void recusarSeEmitida(TransportNote n) {
        if (n.getStatus() != TransportNoteStatus.DRAFT) {
            throw ApiException.conflict("Uma guia "
                    + n.getStatus().label().toLowerCase()
                    + " já não se altera. Anule-a e emita outra.");
        }
    }

    private TransportNote require(String orgId, String id) {
        return notes.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Guia de transporte não encontrada."));
    }

    private static TransportNoteStatus parse(String v) {
        try {
            return TransportNoteStatus.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado de guia desconhecido: " + v);
        }
    }

    private static String obrigatorio(String v, String mensagem) {
        if (v == null || v.isBlank()) {
            throw ApiException.badRequest(mensagem);
        }
        return v.trim();
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
