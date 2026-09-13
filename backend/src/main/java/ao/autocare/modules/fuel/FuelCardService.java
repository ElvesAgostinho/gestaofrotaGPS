package ao.autocare.modules.fuel;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.FuelCardTransaction;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.importer.Csv;
import ao.autocare.modules.importer.dto.ImportDtos.ImportReport;
import ao.autocare.modules.importer.dto.ImportDtos.RowError;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.FuelCardTransactionRepository;
import ao.autocare.repo.FuelRecordRepository;
import ao.autocare.repo.OrganizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O extrato do cartão de combustível cruzado com o que se registou.
 *
 * <p>O extrato é o que foi <b>pago</b>; os registos são o que os motoristas
 * <b>disseram</b>. Quando batem, tudo bem. Quando o cartão pagou e ninguém
 * registou, ou registou-se sem o cartão pagar, abre-se uma anomalia com os
 * litros e o dinheiro em causa — para alguém perguntar, não para acusar.
 *
 * <p>Uma linha bate com um registo quando é do mesmo ativo (ou do mesmo
 * cartão), à distância de ±36 h, com litros a ±5 % (ou o mesmo valor pago).
 */
@Service
public class FuelCardService {

    private static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");
    private static final int JANELA_HORAS = 36;

    private static final String[] COL_DATE = {"data", "data da transacao", "date", "data hora"};
    private static final String[] COL_CARD = {"cartao", "numero do cartao", "card", "n cartao"};
    private static final String[] COL_ASSET = {"ativo", "viatura", "matricula", "tag", "etiqueta", "asset"};
    private static final String[] COL_LITERS = {"litros", "quantidade", "liters", "qtd"};
    private static final String[] COL_AMOUNT = {"valor", "montante", "total", "amount", "custo"};
    private static final String[] COL_STATION = {"posto", "estacao", "local", "station"};
    private static final String[] COL_REF = {"referencia", "transacao", "recibo", "reference", "id"};

    private final FuelCardTransactionRepository transactions;
    private final FuelRecordRepository records;
    private final FuelAnomalyRepository anomalies;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public FuelCardService(FuelCardTransactionRepository transactions, FuelRecordRepository records,
            FuelAnomalyRepository anomalies, AssetRepository assets, OrganizationRepository organizations,
            AuditService audit) {
        this.transactions = transactions;
        this.records = records;
        this.anomalies = anomalies;
        this.assets = assets;
        this.organizations = organizations;
        this.audit = audit;
    }

    public String template() {
        return """
                data;cartao;matricula;litros;valor;posto;referencia
                12/09/2026 14:32;CARD-4412;LD-11-22-AA;180;72000;Sonangol Viana;TX-99812
                13/09/2026 08:05;CARD-4412;LD-11-22-AA;60;24000;Pumangol Cacuaco;TX-99931
                """;
    }

    public record Resultado(ImportReport report, int matched, int unmatched, int recordsWithoutCard) {}

    /** Importa o extrato e cruza-o de imediato. */
    @Transactional
    public Resultado importar(String orgId, String userId, String content, boolean dryRun) {
        Csv.Sheet sheet = Csv.parse(content);
        if (sheet.rows().isEmpty()) {
            throw ApiException.badRequest("O ficheiro não tem linhas de dados.");
        }
        Map<String, Asset> porTag = new LinkedHashMap<>();
        Map<String, Asset> porMatricula = new LinkedHashMap<>();
        Map<String, Asset> porCartao = new LinkedHashMap<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            porTag.put(chave(a.getTag()), a);
            if (a.getPlate() != null && !a.getPlate().isBlank()) porMatricula.put(chave(a.getPlate()), a);
        }
        // Um cartão que já apareceu em registos de um ativo identifica esse ativo.
        for (FuelRecord r : records.forOrgBetween(orgId, Instant.now().minus(365, ChronoUnit.DAYS), Instant.now().plusSeconds(1))) {
            if (r.getCardNumber() != null && !r.getCardNumber().isBlank()) {
                porCartao.putIfAbsent(chave(r.getCardNumber()), r.getAsset());
            }
        }

        String lote = UUID.randomUUID().toString();
        List<RowError> erros = new ArrayList<>();
        List<FuelCardTransaction> novas = new ArrayList<>();
        int repetidas = 0;
        for (Csv.Row row : sheet.rows()) {
            String ref = row.getAny(COL_ASSET);
            try {
                Instant quando = data(row.getAny(COL_DATE));
                if (quando == null) throw new IllegalArgumentException("Falta a data (dd/mm/aaaa hh:mm).");
                BigDecimal litros = decimal(row.getAny(COL_LITERS));
                BigDecimal valor = decimal(row.getAny(COL_AMOUNT));
                if (litros == null && valor == null) throw new IllegalArgumentException("Falta os litros ou o valor.");
                String cartao = row.getAny(COL_CARD);
                Asset ativo = null;
                if (ref != null && !ref.isBlank()) {
                    ativo = porTag.get(chave(ref));
                    if (ativo == null) ativo = porMatricula.get(chave(ref));
                    if (ativo == null) throw new IllegalArgumentException("Não há nenhuma viatura \"" + ref + "\".");
                } else if (cartao != null && !cartao.isBlank()) {
                    ativo = porCartao.get(chave(cartao));
                }
                if (transactions.existsByOrganizationIdAndCardNumberAndTransactedAtAndLitersAndAmount(
                        orgId, cartao != null ? cartao.trim() : null, quando, litros, valor)) {
                    repetidas++;
                    continue;
                }
                FuelCardTransaction t = new FuelCardTransaction();
                t.setOrganization(organizations.getReferenceById(orgId));
                t.setAsset(ativo);
                t.setCardNumber(cartao != null && !cartao.isBlank() ? cartao.trim() : null);
                t.setTransactedAt(quando);
                t.setLiters(litros);
                t.setAmount(valor);
                t.setStation(blank(row.getAny(COL_STATION)));
                t.setReference(blank(row.getAny(COL_REF)));
                t.setImportBatch(lote);
                novas.add(t);
            } catch (IllegalArgumentException | ApiException e) {
                erros.add(new RowError(row.lineNumber(), ref, e.getMessage()));
            }
        }

        int matched = 0;
        int unmatched = 0;
        int semCartao = 0;
        if (!dryRun) {
            transactions.saveAll(novas);
            Cruzamento c = cruzar(orgId, novas);
            matched = c.matched;
            unmatched = c.unmatched;
            semCartao = c.recordsWithoutCard;
            audit.record(orgId, userId, "import.fuel_cards", "Organization", orgId,
                    novas.size() + " transações, " + matched + " a bater, " + unmatched + " sem registo");
        } else {
            // No ensaio diz-se quantas bateriam, sem gravar.
            Cruzamento c = cruzar(orgId, novas);
            matched = c.matched;
            unmatched = c.unmatched;
            semCartao = c.recordsWithoutCard;
        }
        ImportReport report = new ImportReport("fuel-cards", dryRun, sheet.rows().size(), novas.size(), 0,
                erros.size() + repetidas, List.of(), erros);
        return new Resultado(report, matched, unmatched, semCartao);
    }

    private record Cruzamento(int matched, int unmatched, int recordsWithoutCard) {}

    /**
     * Cruza as transações com os registos da janela. Em modo de gravação, as
     * transações já estão guardadas e as anomalias abrem-se; em ensaio só se
     * contam.
     */
    private Cruzamento cruzar(String orgId, List<FuelCardTransaction> lote) {
        if (lote.isEmpty()) {
            return new Cruzamento(0, 0, 0);
        }
        Instant min = lote.stream().map(FuelCardTransaction::getTransactedAt).min(Instant::compareTo).orElseThrow();
        Instant max = lote.stream().map(FuelCardTransaction::getTransactedAt).max(Instant::compareTo).orElseThrow();
        List<FuelRecord> registos = records.forOrgBetween(orgId,
                min.minus(JANELA_HORAS, ChronoUnit.HOURS), max.plus(JANELA_HORAS, ChronoUnit.HOURS));
        Set<String> usados = new HashSet<>();
        boolean gravar = lote.get(0).getId() != null;
        int matched = 0;
        int unmatched = 0;

        for (FuelCardTransaction t : lote) {
            FuelRecord melhor = null;
            long melhorDist = Long.MAX_VALUE;
            for (FuelRecord r : registos) {
                if (usados.contains(r.getId())) continue;
                if (!mesmoAlvo(t, r)) continue;
                long dist = Math.abs(ChronoUnit.MINUTES.between(t.getTransactedAt(), r.getFilledAt()));
                if (dist > JANELA_HORAS * 60L) continue;
                if (!quantidadeBate(t, r)) continue;
                if (dist < melhorDist) {
                    melhor = r;
                    melhorDist = dist;
                }
            }
            if (melhor != null) {
                usados.add(melhor.getId());
                matched++;
                if (gravar) {
                    t.setStatus(FuelCardTransaction.Status.MATCHED);
                    t.setFuelRecord(melhor);
                    if (t.getAsset() == null) t.setAsset(melhor.getAsset());
                }
            } else {
                unmatched++;
                if (gravar) {
                    t.setStatus(FuelCardTransaction.Status.UNMATCHED);
                    abrirAnomalia(t);
                }
            }
        }

        // Registos com cartão na janela que nenhuma transação pagou.
        int semCartao = 0;
        for (FuelRecord r : registos) {
            if (usados.contains(r.getId())) continue;
            if (r.getCardNumber() == null || r.getCardNumber().isBlank()) continue;
            boolean cartaoNoLote = lote.stream().anyMatch(t -> t.getCardNumber() != null
                    && chave(t.getCardNumber()).equals(chave(r.getCardNumber())));
            if (!cartaoNoLote) continue; // o extrato deste cartão não veio; não se conclui nada
            if (r.getFilledAt().isBefore(min.minus(JANELA_HORAS, ChronoUnit.HOURS))
                    || r.getFilledAt().isAfter(max.plus(JANELA_HORAS, ChronoUnit.HOURS))) continue;
            semCartao++;
            if (gravar && !anomalies.existsByFuelRecordIdAndKind(r.getId(), FuelAnomalyKind.RECORD_WITHOUT_CARD)) {
                FuelAnomaly a = new FuelAnomaly();
                a.setOrganization(r.getOrganization());
                a.setAsset(r.getAsset());
                a.setFuelRecord(r);
                a.setDriver(r.getDriver());
                a.setKind(FuelAnomalyKind.RECORD_WITHOUT_CARD);
                a.setSeverity(AlertSeverity.WARNING);
                a.setDetectedAt(Instant.now());
                a.setOccurredAt(r.getFilledAt());
                a.setObservedValue(r.getLiters());
                a.setUnit("L");
                a.setLitersAtRisk(r.getLiters());
                a.setCurrency(r.getCurrency());
                a.setCostAtRisk(r.getTotalCost());
                a.setTitle("Registado, mas o cartão " + r.getCardNumber() + " não pagou");
                a.setDetail("O abastecimento de " + r.getLiters() + " L em " + DateTimeFormatter.ofPattern("dd/MM HH:mm")
                        .format(r.getFilledAt().atZone(LUANDA)) + " não aparece no extrato do cartão. "
                        + "Confirme com o talão do posto.");
                anomalies.save(a);
            }
        }
        return new Cruzamento(matched, unmatched, semCartao);
    }

    private void abrirAnomalia(FuelCardTransaction t) {
        FuelAnomaly a = new FuelAnomaly();
        a.setOrganization(t.getOrganization());
        a.setAsset(t.getAsset());
        a.setKind(FuelAnomalyKind.CARD_WITHOUT_RECORD);
        a.setSeverity(AlertSeverity.CRITICAL);
        a.setDetectedAt(Instant.now());
        a.setOccurredAt(t.getTransactedAt());
        a.setObservedValue(t.getLiters());
        a.setUnit("L");
        a.setLitersAtRisk(t.getLiters());
        a.setCurrency(t.getCurrency());
        a.setCostAtRisk(t.getAmount());
        String quem = t.getAsset() != null ? t.getAsset().getTag() : (t.getCardNumber() != null ? "cartão " + t.getCardNumber() : "cartão");
        a.setTitle("O cartão pagou e ninguém registou — " + quem);
        a.setDetail((t.getLiters() != null ? t.getLiters() + " L" : "") + (t.getAmount() != null ? " · " + t.getAmount() + " " + t.getCurrency() : "")
                + " em " + DateTimeFormatter.ofPattern("dd/MM HH:mm").format(t.getTransactedAt().atZone(LUANDA))
                + (t.getStation() != null ? " · " + t.getStation() : "")
                + ". Não há nenhum abastecimento registado nesta viatura nas 36 h à volta. Para onde foi o combustível?");
        anomalies.save(a);
    }

    private static boolean mesmoAlvo(FuelCardTransaction t, FuelRecord r) {
        if (t.getAsset() != null) {
            return t.getAsset().getId().equals(r.getAsset().getId());
        }
        return t.getCardNumber() != null && r.getCardNumber() != null
                && chave(t.getCardNumber()).equals(chave(r.getCardNumber()));
    }

    private static boolean quantidadeBate(FuelCardTransaction t, FuelRecord r) {
        if (t.getLiters() != null && r.getLiters() != null && r.getLiters().signum() > 0) {
            BigDecimal desvio = t.getLiters().subtract(r.getLiters()).abs()
                    .divide(r.getLiters(), 4, RoundingMode.HALF_UP);
            if (desvio.compareTo(new BigDecimal("0.05")) <= 0) return true;
        }
        if (t.getAmount() != null && r.getTotalCost() != null && r.getTotalCost().signum() > 0) {
            BigDecimal desvio = t.getAmount().subtract(r.getTotalCost()).abs()
                    .divide(r.getTotalCost(), 4, RoundingMode.HALF_UP);
            return desvio.compareTo(new BigDecimal("0.02")) <= 0;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<FuelCardTransaction> list(String orgId, FuelCardTransaction.Status status) {
        return status == null ? transactions.findByOrganizationIdOrderByTransactedAtDesc(orgId)
                : transactions.findByOrganizationIdAndStatusOrderByTransactedAtDesc(orgId, status);
    }

    @Transactional
    public void ignore(String orgId, String userId, String id) {
        FuelCardTransaction t = transactions.findById(id)
                .filter(x -> x.getOrganization().getId().equals(orgId))
                .orElseThrow(() -> ApiException.notFound("Transação não encontrada."));
        t.setStatus(FuelCardTransaction.Status.IGNORED);
        audit.record(orgId, userId, "fuel_card.ignore", "FuelCardTransaction", id, t.getReference());
    }

    // -----------------------------------------------------------------------

    private static Instant data(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignored) {
            // formatos de folha abaixo
        }
        for (String p : new String[] {"dd/MM/yyyy HH:mm", "dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm:ss"}) {
            try {
                return java.time.LocalDateTime.parse(t, DateTimeFormatter.ofPattern(p)).atZone(LUANDA).toInstant();
            } catch (Exception ignored) {
                // próximo
            }
        }
        for (String p : new String[] {"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd"}) {
            try {
                return java.time.LocalDate.parse(t, DateTimeFormatter.ofPattern(p)).atTime(12, 0).atZone(LUANDA).toInstant();
            } catch (Exception ignored) {
                // próximo
            }
        }
        return null;
    }

    private static BigDecimal decimal(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim().replace(" ", "").replace(" ", "");
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

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
