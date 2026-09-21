package ao.autocare.modules.asset;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.DriverAssignment;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverAssignmentRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O abate: quando uma viatura, máquina ou gerador sai da frota.
 *
 * <p>Toda a gente tem isto e quase ninguém o trata bem. Vende-se um camião e
 * ele fica na lista a fingir que existe, ou apaga-se — e com ele desaparece o
 * histórico que dizia quanto custou. As duas coisas estão erradas: o ativo tem
 * de sair das listas e dos indicadores, e o que se gastou nele tem de ficar,
 * porque é a única maneira de responder à pergunta seguinte, que é sempre a
 * mesma: <b>valeu a pena?</b>
 *
 * <p>Por isso um abate regista a data, o motivo, o contador final e quanto
 * rendeu, fecha o que estava aberto — atribuições de motorista — e devolve o
 * custo de vida do ativo. Também se pode reverter: enganos acontecem, e uma
 * viatura vendida que afinal não foi vendida não deve obrigar a criar outra.
 */
@Service
public class AssetRetirementService {

    /** Os motivos possíveis, em linguagem de quem gere uma frota. */
    public enum Motivo {
        SOLD("Vendido"),
        SCRAPPED("Sucata"),
        ACCIDENT("Perda total por acidente"),
        THEFT("Roubo"),
        END_OF_LIFE("Fim de vida útil"),
        RETURNED("Devolvido ao locador"),
        OTHER("Outro");

        private final String label;

        Motivo(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** O que se devolve depois de abater — e o que a ficha mostra. */
    public record Abate(
            String assetId,
            String tag,
            String name,
            Instant retiredAt,
            String reason,
            String reasonLabel,
            String notes,
            BigDecimal finalMeter,
            String meterUnit,
            BigDecimal residualValue,
            String currency,
            /** O que custou em manutenção ao longo da vida. */
            BigDecimal lifetimeMaintenanceCost,
            /** Quanto custou cada km ou hora, na vida inteira. */
            BigDecimal costPerUnit,
            int workOrders) {}

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final DriverAssignmentRepository driverAssignments;
    private final WorkOrderRepository workOrders;
    private final AuditService audit;

    public AssetRetirementService(AssetRepository assets, AssetMeterRepository meters,
            DriverAssignmentRepository driverAssignments, WorkOrderRepository workOrders,
            AuditService audit) {
        this.assets = assets;
        this.meters = meters;
        this.driverAssignments = driverAssignments;
        this.workOrders = workOrders;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Abate> listar(String orgId) {
        return assets.findByOrganizationId(orgId).stream()
                .filter(a -> a.getRetiredAt() != null)
                .sorted((x, y) -> y.getRetiredAt().compareTo(x.getRetiredAt()))
                .map(this::montar)
                .toList();
    }

    @Transactional
    public Abate abater(String orgId, String userId, String assetId, String motivo,
            Instant quando, BigDecimal contadorFinal, BigDecimal valorResidual, String notas) {

        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
        if (a.getRetiredAt() != null) {
            throw ApiException.conflict("Este ativo já foi abatido em "
                    + a.getRetiredAt().atZone(java.time.ZoneId.of("Africa/Luanda")).toLocalDate() + ".");
        }

        // Ordens por fechar: não se abate por cima de trabalho em curso. Ou se
        // conclui, ou se anula — as duas coisas deixam rasto; abater não.
        long abertas = workOrders.countOpenForAsset(a.getId());
        if (abertas > 0) {
            throw ApiException.conflict("Esta viatura ainda tem " + abertas
                    + " ordem(ns) por fechar. Conclua-as ou anule-as antes de abater.");
        }

        Motivo m;
        try {
            m = motivo == null || motivo.isBlank() ? Motivo.OTHER : Motivo.valueOf(motivo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Motivo desconhecido: " + motivo);
        }

        AssetMeter principal = principal(a);
        BigDecimal contador = contadorFinal != null ? contadorFinal
                : (principal != null ? principal.getCurrentValue() : null);

        a.setRetiredAt(quando != null ? quando : Instant.now());
        a.setRetiredReason(m.name());
        a.setRetiredNotes(notas != null && notas.length() > 1000 ? notas.substring(0, 1000) : notas);
        a.setRetiredMeter(contador);
        a.setResidualValue(valorResidual);
        a.setRetiredBy(userId);
        a.setStatus(AssetStatus.RETIRED);
        a.setArchived(true);

        // Quem conduzia deixa de conduzir: uma atribuição aberta numa viatura
        // que já não existe é o tipo de lixo que só se descobre num relatório.
        for (DriverAssignment at : driverAssignments.openForAsset(a.getId())) {
            if (at.getEndedAt() == null) {
                at.setEndedAt(a.getRetiredAt());
            }
        }

        audit.record(orgId, userId, "asset.retire", "Asset", a.getId(),
                a.getTag() + " · " + m.label()
                        + (valorResidual != null ? " · " + valorResidual : ""));

        return montar(a);
    }

    @Transactional
    public Abate reverter(String orgId, String userId, String assetId) {
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
        if (a.getRetiredAt() == null) {
            throw ApiException.badRequest("Este ativo não está abatido.");
        }
        a.setRetiredAt(null);
        a.setRetiredReason(null);
        a.setRetiredNotes(null);
        a.setRetiredMeter(null);
        a.setResidualValue(null);
        a.setRetiredBy(null);
        a.setArchived(false);
        a.setStatus(AssetStatus.OPERATIONAL);
        audit.record(orgId, userId, "asset.unretire", "Asset", a.getId(), a.getTag());
        return montar(a);
    }

    private AssetMeter principal(Asset a) {
        return meters.findByAssetId(a.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst().orElse(null);
    }

    private Abate montar(Asset a) {
        AssetMeter principal = principal(a);
        String unidade = principal != null
                ? (principal.getKind() == ao.autocare.domain.enums.Enums.MeterKind.HOURMETER ? "h" : "km")
                : null;
        BigDecimal contador = a.getRetiredMeter() != null ? a.getRetiredMeter()
                : (principal != null ? principal.getCurrentValue() : null);

        BigDecimal custo = workOrders.totalCostForAsset(a.getId());
        if (custo == null) {
            custo = BigDecimal.ZERO;
        }
        int ordens = (int) workOrders.countByAssetId(a.getId());
        BigDecimal porUnidade = contador != null && contador.signum() > 0
                ? custo.divide(contador, 2, java.math.RoundingMode.HALF_UP) : null;

        Motivo m = a.getRetiredReason() != null ? Motivo.valueOf(a.getRetiredReason()) : null;
        return new Abate(a.getId(), a.getTag(), a.getName(), a.getRetiredAt(),
                a.getRetiredReason(), m != null ? m.label() : null, a.getRetiredNotes(),
                contador, unidade, a.getResidualValue(), a.getCurrency(),
                custo, porUnidade, ordens);
    }
}
