package ao.autocare.modules.transport;

import static ao.autocare.modules.org.PdfRenderer.data;
import static ao.autocare.modules.org.PdfRenderer.dataHora;
import static ao.autocare.modules.org.PdfRenderer.numero;

import ao.autocare.common.ApiException;
import ao.autocare.domain.TransportNote;
import ao.autocare.domain.TransportNoteItem;
import ao.autocare.modules.org.DocumentSealService;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import ao.autocare.repo.TransportNoteRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A guia de transporte em papel.
 *
 * <p>Vai na cabina, mostra-se num posto de controlo, assina-se na entrega. É
 * o documento que a empresa mais imprime e o que um fiscal mais pede — e não
 * existia em PDF.
 */
@Service
public class TransportNotePdfService {

    public record Doc(
            String number, String statusLabel, String issuedAt,
            String originLabel, String originAddress, String destinationLabel,
            String destinationAddress, String customerName, String customerTaxId,
            String customerContact,
            String assetTag, String plate, String trailerPlate, String driver, String driverLicense,
            String departedAt, String deliveredAt, String departureMeter, String arrivalMeter,
            String cargoDescription, String hazardClass, List<Line> items,
            String totalWeightKg, String totalPackages,
            String notes, String receivedByName, String receivedByDocument, String deliveryNotes) {}

    public record Line(String description, String reference, String quantity, String unit,
                       String weightKg, String packages) {}

    private final TransportNoteRepository notes;
    private final Letterhead letterhead;
    private final PdfRenderer renderer;

    public TransportNotePdfService(TransportNoteRepository notes, Letterhead letterhead,
            PdfRenderer renderer) {
        this.notes = notes;
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String noteId) {
        return render(orgId, noteId, null);
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String noteId, DocumentSealService.Selo selo) {
        TransportNote g = notes.findByIdAndOrganizationId(noteId, orgId)
                .orElseThrow(() -> ApiException.notFound("Guia não encontrada."));

        List<Line> linhas = new ArrayList<>();
        for (TransportNoteItem it : g.getItems()) {
            linhas.add(new Line(it.getDescription(), it.getReference(),
                    numero(it.getQuantity(), 0), it.getUnit(),
                    numero(it.getWeightKg(), 0),
                    it.getPackages() == null ? null : String.valueOf(it.getPackages())));
        }

        String condutor = g.getDriver() != null ? g.getDriver().getName() : g.getDriverLabel();
        String carta = g.getDriverLicense() != null ? g.getDriverLicense()
                : (g.getDriver() != null ? g.getDriver().getLicenseNumber() : null);

        Doc doc = new Doc(
                g.getNumber(), g.getStatus().label(), dataHora(g.getIssuedAt()),
                g.getOriginLabel(), g.getOriginAddress(), g.getDestinationLabel(),
                g.getDestinationAddress(), g.getCustomerName(), g.getCustomerTaxId(),
                g.getCustomerContact(),
                g.getAsset() != null ? g.getAsset().getTag() : "—",
                g.getAsset() != null ? g.getAsset().getPlate() : null,
                g.getTrailerPlate(), condutor, carta,
                dataHora(g.getDepartedAt()), dataHora(g.getDeliveredAt()),
                numero(g.getDepartureMeter(), 0), numero(g.getArrivalMeter(), 0),
                g.getCargoDescription(), g.getHazardClass(), linhas,
                numero(g.getTotalWeightKg(), 0),
                g.getTotalPackages() == null ? null : String.valueOf(g.getTotalPackages()),
                g.getNotes(), g.getReceivedByName(), g.getReceivedByDocument(),
                g.getDeliveryNotes());

        return renderer.render("transport-note", letterhead.of(g.getOrganization()), "g", doc, selo);
    }
}
