package ao.autocare.modules.purchase;

import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.PurchaseReceipt;
import ao.autocare.domain.PurchaseReceiptLine;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.SupplierInvoice;
import ao.autocare.repo.PurchaseOrderRepository;
import ao.autocare.repo.PurchaseReceiptRepository;
import ao.autocare.repo.RfqSupplierRepository;
import ao.autocare.repo.SupplierInvoiceRepository;
import ao.autocare.repo.SupplierRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Como é que este fornecedor se tem portado.
 *
 * <p>Toda a gente tem uma opinião sobre os fornecedores — «aquele é caro»,
 * «aquele atrasa sempre» — e quase nunca tem números. A opinião muda com o
 * último episódio e esquece os vinte anteriores; os números não.
 *
 * <p>Quatro medidas, todas calculadas do que já aconteceu e nenhuma escrita à
 * mão. <b>Pontualidade</b>: entregou até à data pedida? <b>Qualidade</b>: o que
 * chegou veio bem, ou houve devoluções? <b>Resposta</b>: responde quando se lhe
 * pede preço, ou fica calado? <b>Facturação</b>: a factura bate com a
 * encomenda, ou vem sempre acima?
 *
 * <p>Não há nota global de propósito. Uma média esconde exactamente o que
 * interessa: um fornecedor pontual que fractura a mais é um problema diferente
 * de um barato que atrasa, e tratá-los com o mesmo número é não os tratar.
 */
@Service
public class SupplierScorecard {

    /** A margem acima da qual uma factura conta como «acima da encomenda». */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    public record Medida(
            String nome,
            /** 0 a 100, ou nulo quando ainda não há como medir. */
            Integer valor,
            String detalhe,
            /** BOM · ATENCAO · MAU · SEM_DADOS */
            String estado) {}

    public record Ficha(
            String supplierId,
            String supplierName,
            int ordens,
            int ordensRecebidas,
            BigDecimal totalComprado,
            String moeda,
            List<Medida> medidas,
            /** O que se pode dizer dele, em português. */
            String leitura) {}

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final PurchaseReceiptRepository receipts;
    private final SupplierInvoiceRepository invoices;
    private final RfqSupplierRepository convidados;

    public SupplierScorecard(
            SupplierRepository suppliers,
            PurchaseOrderRepository orders,
            PurchaseReceiptRepository receipts,
            SupplierInvoiceRepository invoices,
            RfqSupplierRepository convidados) {
        this.suppliers = suppliers;
        this.orders = orders;
        this.receipts = receipts;
        this.invoices = invoices;
        this.convidados = convidados;
    }

    @Transactional(readOnly = true)
    public Ficha de(String orgId, String supplierId) {
        Supplier s = suppliers.findByIdAndOrganizationId(supplierId, orgId)
                .orElseThrow(() -> ao.autocare.common.ApiException.notFound(
                        "Fornecedor não encontrado."));

        List<PurchaseOrder> dele = orders.findAllForOrg(orgId).stream()
                .filter(o -> o.getSupplier() != null && o.getSupplier().getId().equals(supplierId))
                .filter(o -> o.getStatus() != PurchaseOrder.Status.DRAFT
                        && o.getStatus() != PurchaseOrder.Status.CANCELLED)
                .toList();

        BigDecimal comprado = dele.stream()
                .map(PurchaseOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Medida> medidas = new ArrayList<>();
        medidas.add(pontualidade(dele));
        medidas.add(qualidade(dele));
        medidas.add(resposta(supplierId));
        medidas.add(facturacao(supplierId));

        int recebidas = (int) dele.stream()
                .filter(o -> o.getStatus() == PurchaseOrder.Status.RECEIVED).count();

        return new Ficha(s.getId(), s.getName(), dele.size(), recebidas,
                comprado.setScale(2, RoundingMode.HALF_UP),
                s.getCurrency() != null ? s.getCurrency() : "AOA",
                medidas, leitura(s.getName(), medidas, dele.size()));
    }

    /**
     * Entregou até à data pedida?
     *
     * <p>Mede-se contra a data que a ordem pedia, não contra a que ele
     * prometeu: o que interessa é se a máquina estava parada ou não.
     */
    private Medida pontualidade(List<PurchaseOrder> dele) {
        int comPrazo = 0;
        int aHoras = 0;
        int atrasoTotal = 0;
        for (PurchaseOrder o : dele) {
            if (o.getExpectedAt() == null || o.getClosedAt() == null
                    || o.getStatus() != PurchaseOrder.Status.RECEIVED) {
                continue;
            }
            comPrazo++;
            LocalDate recebida = o.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (!recebida.isAfter(o.getExpectedAt())) {
                aHoras++;
            } else {
                atrasoTotal += (int) java.time.temporal.ChronoUnit.DAYS
                        .between(o.getExpectedAt(), recebida);
            }
        }
        if (comPrazo == 0) {
            return new Medida("Pontualidade", null,
                    "Ainda não há encomendas recebidas com data pedida.", "SEM_DADOS");
        }
        int pct = aHoras * 100 / comPrazo;
        int atrasadas = comPrazo - aHoras;
        String detalhe = aHoras + " de " + comPrazo + " entregas a horas"
                + (atrasadas > 0
                        ? "; as atrasadas somaram " + atrasoTotal + " dias de atraso" : "");
        return new Medida("Pontualidade", pct, detalhe, estado(pct, 90, 70));
    }

    /** O que chegou veio bem? */
    private Medida qualidade(List<PurchaseOrder> dele) {
        BigDecimal recebido = BigDecimal.ZERO;
        BigDecimal recusado = BigDecimal.ZERO;
        int entregas = 0;
        for (PurchaseOrder o : dele) {
            for (PurchaseReceipt r : receipts.findByOrderIdOrderByReceivedAtAsc(o.getId())) {
                entregas++;
                for (PurchaseReceiptLine l : r.getLines()) {
                    recebido = recebido.add(l.getQuantity());
                    recusado = recusado.add(l.getRejectedQuantity());
                }
            }
        }
        BigDecimal total = recebido.add(recusado);
        if (total.signum() <= 0) {
            return new Medida("Qualidade", null, "Ainda não houve entregas registadas.",
                    "SEM_DADOS");
        }
        int pct = recebido.multiply(new BigDecimal("100"))
                .divide(total, 0, RoundingMode.HALF_UP).intValue();
        String detalhe = recusado.signum() > 0
                ? "Devolvidas " + recusado.stripTrailingZeros().toPlainString()
                        + " unidades em " + entregas + " entregas"
                : "Nenhuma devolução em " + entregas + " entregas";
        return new Medida("Qualidade", pct, detalhe, estado(pct, 98, 90));
    }

    /**
     * Responde quando se lhe pede preço?
     *
     * <p>Quem não responde faz perder uma semana em cada pedido, e isso não
     * aparece em lado nenhum até se contar.
     */
    private Medida resposta(String supplierId) {
        Object[] r = convidados.respostasDe(supplierId);
        // A consulta devolve uma linha com duas contagens, embrulhada.
        Object[] valores = r != null && r.length == 1 && r[0] instanceof Object[] interno
                ? interno : r;
        long pedidos = valores != null && valores.length > 0 && valores[0] != null
                ? ((Number) valores[0]).longValue() : 0;
        long respostas = valores != null && valores.length > 1 && valores[1] != null
                ? ((Number) valores[1]).longValue() : 0;
        if (pedidos == 0) {
            return new Medida("Resposta a pedidos", null,
                    "Ainda não se lhe pediu nenhuma cotação.", "SEM_DADOS");
        }
        int pct = (int) (respostas * 100 / pedidos);
        return new Medida("Resposta a pedidos", pct,
                "Respondeu a " + respostas + " de " + pedidos + " pedidos de cotação",
                estado(pct, 80, 50));
    }

    /** A factura bate com a encomenda? */
    private Medida facturacao(String supplierId) {
        List<SupplierInvoice> dele = invoices.findBySupplierIdOrderByIssuedAtDesc(supplierId);
        int comOrdem = 0;
        int acima = 0;
        BigDecimal excesso = BigDecimal.ZERO;
        for (SupplierInvoice f : dele) {
            if (f.getOrder() == null || f.getAmount() == null || f.getOrder().getTotal() == null) {
                continue;
            }
            comOrdem++;
            BigDecimal diferenca = f.getAmount().subtract(f.getOrder().getTotal());
            if (diferenca.compareTo(TOLERANCIA) > 0) {
                acima++;
                excesso = excesso.add(diferenca);
            }
        }
        if (comOrdem == 0) {
            return new Medida("Facturação", null,
                    "Ainda não há facturas ligadas a encomendas.", "SEM_DADOS");
        }
        int pct = (comOrdem - acima) * 100 / comOrdem;
        String detalhe = acima > 0
                ? acima + " de " + comOrdem + " facturas acima da encomenda, num total de "
                        + excesso.setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "As " + comOrdem + " facturas batem com as encomendas";
        return new Medida("Facturação", pct, detalhe, estado(pct, 95, 80));
    }

    private static String estado(int pct, int bom, int atencao) {
        return pct >= bom ? "BOM" : pct >= atencao ? "ATENCAO" : "MAU";
    }

    /** O que se pode dizer dele, sem inventar uma nota global. */
    private static String leitura(String nome, List<Medida> medidas, int ordens) {
        if (ordens == 0) {
            return "Ainda não houve nenhuma encomenda a " + nome + ".";
        }
        List<String> maus = medidas.stream()
                .filter(m -> "MAU".equals(m.estado())).map(Medida::nome).toList();
        List<String> bons = medidas.stream()
                .filter(m -> "BOM".equals(m.estado())).map(Medida::nome).toList();
        if (!maus.isEmpty()) {
            return nome + " falha em " + String.join(" e ", maus).toLowerCase()
                    + (bons.isEmpty() ? "." : ", mas cumpre em "
                            + String.join(" e ", bons).toLowerCase() + ".");
        }
        if (bons.size() == medidas.size()) {
            return nome + " cumpre em tudo o que há para medir.";
        }
        return bons.isEmpty()
                ? "Ainda não há medições suficientes sobre " + nome + "."
                : nome + " cumpre em " + String.join(" e ", bons).toLowerCase() + ".";
    }
}
