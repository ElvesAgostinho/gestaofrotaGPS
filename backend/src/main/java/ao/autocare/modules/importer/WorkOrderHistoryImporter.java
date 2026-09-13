package ao.autocare.modules.importer;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.importer.dto.ImportDtos.ImportReport;
import ao.autocare.modules.importer.dto.ImportDtos.RowError;
import ao.autocare.modules.meter.MeterService;
import ao.autocare.modules.workorder.CounterService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.SupplierRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa o histórico de manutenção que o cliente traz do Excel.
 *
 * <p>Um cliente novo tem anos de ordens numa folha: data, viatura, o que foi
 * feito, quanto custou, em que oficina. Sem isso o sistema começa do zero e
 * o «custo por viatura» e o «vale a pena reparar?» só se vêem daqui a um
 * ano. Cada linha entra como uma ordem <b>já concluída</b>, com o número
 * original do cliente guardado (ou um número nosso), o contador da altura
 * e o custo total. Não passa pelo fluxo normal (abrir → iniciar → concluir):
 * é história, não trabalho a fazer.
 */
@Service
public class WorkOrderHistoryImporter {

    private static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");

    private static final String[] COL_ASSET = {"ativo", "viatura", "tag", "etiqueta", "matricula", "asset"};
    private static final String[] COL_NUMBER = {"numero", "n", "nº", "ordem", "referencia", "number"};
    private static final String[] COL_DATE = {"data", "data da ordem", "aberta em", "concluida em", "date"};
    private static final String[] COL_TYPE = {"tipo", "type"};
    private static final String[] COL_TITLE = {"titulo", "descricao", "servico", "trabalho", "title"};
    private static final String[] COL_DETAIL = {"detalhe", "observacoes", "notas", "resolucao", "notes"};
    private static final String[] COL_METER = {"contador", "hodometro", "odometro", "horimetro", "km", "horas", "meter"};
    private static final String[] COL_LABOR = {"mao de obra", "mão de obra", "custo mao de obra", "labor"};
    private static final String[] COL_PARTS = {"pecas", "custo pecas", "parts"};
    private static final String[] COL_COST = {"custo", "custo total", "total", "valor", "montante", "cost"};
    private static final String[] COL_SUPPLIER = {"oficina", "fornecedor", "supplier"};
    private static final String[] COL_DOWNTIME = {"horas paradas", "paragem", "downtime"};

    private final WorkOrderRepository workOrders;
    private final AssetRepository assets;
    private final SupplierRepository suppliers;
    private final OrganizationRepository organizations;
    private final CounterService counters;
    private final MeterService meters;
    private final AuditService audit;

    public WorkOrderHistoryImporter(WorkOrderRepository workOrders, AssetRepository assets,
            SupplierRepository suppliers, OrganizationRepository organizations, CounterService counters,
            MeterService meters, AuditService audit) {
        this.workOrders = workOrders;
        this.assets = assets;
        this.suppliers = suppliers;
        this.organizations = organizations;
        this.counters = counters;
        this.meters = meters;
        this.audit = audit;
    }

    public String template() {
        return """
                ativo;numero;data;tipo;titulo;detalhe;contador;oficina;mao de obra;pecas;custo total;horas paradas
                CAM-101;OS-2024-031;12/03/2024;corretiva;Substituicao de embraiagem;Disco e prato novos;154200;Oficina Central;85000;420000;505000;16
                CAM-101;;30/06/2024;preventiva;Revisao dos 160 000 km;Oleo, filtros e correias;160100;;45000;120000;165000;6
                """;
    }

    /**
     * Sem transação a envolver o lote: cada linha grava ou falha por si, para
     * o relatório «120 criadas, 3 erros» ser verdade e as 120 ficarem.
     */
    public ImportReport importar(String orgId, String userId, String content, boolean dryRun) {
        Csv.Sheet sheet = Csv.parse(content);
        if (sheet.rows().isEmpty()) {
            throw ApiException.badRequest("O ficheiro não tem linhas de dados.");
        }
        Map<String, Asset> byTag = new LinkedHashMap<>();
        Map<String, Asset> byPlate = new LinkedHashMap<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            byTag.put(chave(a.getTag()), a);
            if (a.getPlate() != null && !a.getPlate().isBlank()) {
                byPlate.put(chave(a.getPlate()), a);
            }
        }

        List<RowError> erros = new ArrayList<>();
        int criadas = 0;
        for (Csv.Row row : sheet.rows()) {
            String ref = row.getAny(COL_ASSET);
            try {
                if (ref == null || ref.isBlank()) {
                    throw new IllegalArgumentException("Falta a viatura (coluna \"ativo\" ou \"matricula\").");
                }
                Asset ativo = byTag.get(chave(ref));
                if (ativo == null) ativo = byPlate.get(chave(ref));
                if (ativo == null) {
                    throw new IllegalArgumentException("Não há nenhuma viatura com a etiqueta ou matrícula \"" + ref + "\".");
                }
                String titulo = row.getAny(COL_TITLE);
                if (titulo == null || titulo.isBlank()) {
                    throw new IllegalArgumentException("Falta o título (o que foi feito).");
                }
                Instant quando = data(row.getAny(COL_DATE));
                if (quando == null) {
                    throw new IllegalArgumentException("Falta a data, ou está num formato que não se lê (use dd/mm/aaaa).");
                }
                if (quando.isAfter(Instant.now())) {
                    throw new IllegalArgumentException("A data está no futuro: isto é histórico.");
                }
                WorkOrderType tipo = tipo(row.getAny(COL_TYPE));
                BigDecimal maoDeObra = decimal(row.getAny(COL_LABOR));
                BigDecimal pecas = decimal(row.getAny(COL_PARTS));
                BigDecimal total = decimal(row.getAny(COL_COST));
                if (total == null && (maoDeObra != null || pecas != null)) {
                    total = (maoDeObra != null ? maoDeObra : BigDecimal.ZERO).add(pecas != null ? pecas : BigDecimal.ZERO);
                }
                String numero = row.getAny(COL_NUMBER);
                if (numero != null && !numero.isBlank()
                        && workOrders.existsByOrganizationIdAndNumber(orgId, numero.trim())) {
                    throw new IllegalArgumentException("Já existe uma ordem com o número \"" + numero.trim() + "\".");
                }

                if (!dryRun) {
                    gravar(orgId, userId, ativo, numero, quando, tipo, titulo, row.getAny(COL_DETAIL),
                            decimal(row.getAny(COL_METER)), row.getAny(COL_SUPPLIER),
                            maoDeObra, pecas, total, decimal(row.getAny(COL_DOWNTIME)));
                }
                criadas++;
            } catch (IllegalArgumentException | ApiException e) {
                erros.add(new RowError(row.lineNumber(), ref, e.getMessage()));
            }
        }
        if (!dryRun) {
            audit.record(orgId, userId, "import.work_orders", "Organization", orgId,
                    criadas + " ordens de histórico, " + erros.size() + " erros");
        }
        return new ImportReport("work-orders", dryRun, sheet.rows().size(), criadas, 0, erros.size(),
                List.of(), erros);
    }

    @Transactional
    void gravar(String orgId, String userId, Asset ativo, String numero, Instant quando, WorkOrderType tipo,
            String titulo, String detalhe, BigDecimal contador, String oficina, BigDecimal maoDeObra,
            BigDecimal pecas, BigDecimal total, BigDecimal horasParadas) {
        int ano = quando.atZone(LUANDA).getYear();
        WorkOrder w = new WorkOrder();
        w.setOrganization(organizations.getReferenceById(orgId));
        w.setAsset(ativo);
        if (numero != null && !numero.isBlank()) {
            w.setNumber(numero.trim());
        } else {
            long seq = counters.next(orgId, "work_order:" + ano);
            w.setNumber(String.format("OM-%d-%06d", ano, seq));
        }
        w.setOrderYear(ano);
        w.setType(tipo);
        w.setPriority(WorkOrderPriority.NORMAL);
        w.setTitle(titulo.trim());
        w.setDescription("Importado do histórico.");
        w.setResolution(detalhe != null && !detalhe.isBlank() ? detalhe.trim() : null);
        w.setStatus(WorkOrderStatus.DONE);
        w.setOpenedAt(quando);
        w.setStartedAt(quando);
        w.setCompletedAt(quando);
        w.setMeterValue(contador);
        w.setTotalLaborCost(maoDeObra);
        w.setTotalPartsCost(pecas);
        w.setTotalCost(total);
        w.setDowntimeHours(horasParadas);
        if (oficina != null && !oficina.isBlank()) {
            suppliers.findByOrganizationIdAndNameIgnoreCase(orgId, oficina.trim()).ifPresent(w::setSupplier);
            if (w.getSupplier() == null) {
                w.setAssignedToLabel(oficina.trim());
            }
        }
        workOrders.save(w);
        // O contador da altura conta como leitura: só faz andar a ficha se for maior do que o atual.
        if (contador != null) {
            meters.recordFromWorkOrder(ativo, contador, quando, userId, "Histórico importado · " + w.getNumber());
        }
    }

    private static WorkOrderType tipo(String v) {
        if (v == null || v.isBlank()) return WorkOrderType.CORRECTIVE;
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("prev")) return WorkOrderType.PREVENTIVE;
        if (t.startsWith("insp")) return WorkOrderType.INSPECTION;
        if (t.startsWith("pred")) return WorkOrderType.PREDICTIVE;
        if (t.startsWith("emerg") || t.startsWith("urg")) return WorkOrderType.EMERGENCY;
        if (t.startsWith("rev") || t.startsWith("over")) return WorkOrderType.OVERHAUL;
        return WorkOrderType.CORRECTIVE;
    }

    private static Instant data(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignored) {
            // formatos de folha abaixo
        }
        for (String padrao : new String[] {"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "d/M/yyyy", "dd/MM/yy"}) {
            try {
                return java.time.LocalDate.parse(t, DateTimeFormatter.ofPattern(padrao))
                        .atTime(12, 0).atZone(LUANDA).toInstant();
            } catch (Exception ignored) {
                // próximo
            }
        }
        return null;
    }

    private static BigDecimal decimal(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim().replace(" ", "").replace(" ", "");
        // 1.250.000,50 → 1250000.50 ; 1,250,000.50 → 1250000.50
        if (t.contains(",") && t.contains(".")) {
            t = t.lastIndexOf(',') > t.lastIndexOf('.') ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else {
            t = t.replace(',', '.');
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + v + "\" não é um número.");
        }
    }

    private static String chave(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }
}
