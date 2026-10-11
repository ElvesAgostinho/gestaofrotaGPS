package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Part;
import ao.autocare.domain.Rfq;
import ao.autocare.domain.RfqLine;
import ao.autocare.domain.RfqQuote;
import ao.autocare.domain.RfqSupplier;
import ao.autocare.domain.Supplier;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.purchase.dto.OrderDtos;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SavePriceRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.AdjudicarRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.LinhaRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.LinhaView;
import ao.autocare.modules.purchase.dto.RfqDtos.PedidoRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.PedidoView;
import ao.autocare.modules.purchase.dto.RfqDtos.PrecoRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.PrecoView;
import ao.autocare.modules.purchase.dto.RfqDtos.PropostaView;
import ao.autocare.modules.purchase.dto.RfqDtos.RespostaRequest;
import ao.autocare.modules.workorder.CounterService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.repo.RfqQuoteRepository;
import ao.autocare.repo.RfqRepository;
import ao.autocare.repo.RfqSupplierRepository;
import ao.autocare.repo.SupplierRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedir três preços deixa de ser uma promessa.
 *
 * <p>Numa obra, «pediram-se três preços» é uma frase que se diz e que ninguém
 * consegue provar. Os preços chegam por WhatsApp, por email e por telefone, a
 * comparação faz-se de cabeça — quando se faz —, e escolhe-se pelo que se
 * lembra em vez de pelo que custa.
 *
 * <p>Duas decisões que valem a pena explicar. A <b>especificação vai no
 * pedido</b>: pedir «óleo do motor» a três fornecedores e receber três preços
 * de três óleos diferentes não é uma comparação. E um fornecedor que não tem
 * uma das linhas <b>não fica desclassificado</b> das outras — há quem tenha os
 * filtros e não tenha os pneus, e obrigar a escolher um só para tudo é pagar
 * mais caro por metade da lista.
 *
 * <p>Ao adjudicar, se não se escolher o mais barato, o motivo é obrigatório.
 * Escolher o mais caro pode ser a decisão certa — chega numa semana em vez de
 * três —, mas tem de ficar escrito porquê. É a diferença entre uma decisão e
 * um favor.
 */
@Service
public class RfqService {

    private final RfqRepository pedidos;
    private final RfqSupplierRepository convidados;
    private final RfqQuoteRepository precos;
    private final SupplierRepository suppliers;
    private final PartRepository parts;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final CounterService counters;
    private final PurchaseOrderService ordens;
    private final SupplierBookService caderno;
    private final AuditService audit;
    private final ao.autocare.repo.StoredFileRepository ficheiros;
    private final ao.autocare.storage.StorageProvider armazenamento;

    public RfqService(
            RfqRepository pedidos,
            RfqSupplierRepository convidados,
            RfqQuoteRepository precos,
            SupplierRepository suppliers,
            PartRepository parts,
            AssetRepository assets,
            OrganizationRepository organizations,
            UserRepository users,
            CounterService counters,
            PurchaseOrderService ordens,
            SupplierBookService caderno,
            AuditService audit,
            ao.autocare.repo.StoredFileRepository ficheiros,
            ao.autocare.storage.StorageProvider armazenamento) {
        this.pedidos = pedidos;
        this.convidados = convidados;
        this.precos = precos;
        this.suppliers = suppliers;
        this.parts = parts;
        this.assets = assets;
        this.organizations = organizations;
        this.users = users;
        this.counters = counters;
        this.ordens = ordens;
        this.caderno = caderno;
        this.audit = audit;
        this.ficheiros = ficheiros;
        this.armazenamento = armazenamento;
    }

    // ==== Criar =============================================================

    @Transactional
    public PedidoView criar(String orgId, String userId, PedidoRequest req) {
        Rfq r = new Rfq();
        r.setOrganization(organizations.getReferenceById(orgId));
        r.setNumber(proximoNumero(orgId));
        r.setTitle(req.title().trim());
        r.setDueDate(req.dueDate());
        r.setNotes(limpo(req.notes()));
        r.setCreatedBy(userId);
        r.setCreatedByLabel(nomeDe(userId));
        aplicarLinhas(orgId, r, req.lines());

        for (String supplierId : req.supplierIds() == null ? List.<String>of() : req.supplierIds()) {
            Supplier s = suppliers.findByIdAndOrganizationId(supplierId, orgId)
                    .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
            RfqSupplier rs = new RfqSupplier();
            rs.setSupplier(s);
            rs.setCurrency(s.getCurrency() != null ? s.getCurrency() : "AOA");
            r.addSupplier(rs);
        }
        if (r.getSuppliers().isEmpty()) {
            throw ApiException.badRequest(
                    "Escolha a quem pedir preço. Um pedido a ninguém não compara nada.");
        }
        pedidos.save(r);

        audit.record(orgId, userId, "rfq.create", "Rfq", r.getId(),
                r.getNumber() + " · " + r.getSuppliers().size() + " fornecedor(es)");
        return ver(orgId, r);
    }

    @Transactional
    public PedidoView alterar(String orgId, String userId, String id, PedidoRequest req) {
        Rfq r = exigir(orgId, id);
        if (r.getStatus() != Rfq.Status.OPEN) {
            throw ApiException.conflict("Um pedido já adjudicado ou anulado não se altera.");
        }
        boolean jaHaRespostas = r.getSuppliers().stream().anyMatch(s -> s.getRespondedAt() != null);
        if (jaHaRespostas) {
            // Mudar as linhas depois de alguém responder invalida a comparação
            // em silêncio: os preços passariam a ser de outra coisa.
            throw ApiException.conflict(
                    "Já há respostas a este pedido. Mudar as linhas agora tornaria a comparação "
                            + "falsa — anule e faça outro pedido.");
        }
        r.setTitle(req.title().trim());
        r.setDueDate(req.dueDate());
        r.setNotes(limpo(req.notes()));
        aplicarLinhas(orgId, r, req.lines());
        audit.record(orgId, userId, "rfq.update", "Rfq", r.getId(), r.getNumber());
        return ver(orgId, r);
    }

    private void aplicarLinhas(String orgId, Rfq r, List<LinhaRequest> linhas) {
        r.getLines().clear();
        int ordem = 0;
        for (LinhaRequest l : linhas == null ? List.<LinhaRequest>of() : linhas) {
            RfqLine linha = new RfqLine();
            linha.setDescription(l.description().trim());
            linha.setSpecification(limpo(l.specification()));
            linha.setQuantity(l.quantity());
            linha.setUnit(limpo(l.unit()));
            linha.setSortOrder(ordem++);
            if (l.partId() != null && !l.partId().isBlank()) {
                Part p = parts.findByIdAndOrganizationId(l.partId(), orgId)
                        .orElseThrow(() -> ApiException.notFound("Peça não encontrada."));
                linha.setPart(p);
                if (linha.getUnit() == null) {
                    linha.setUnit(p.getUnit());
                }
                // A norma vai no pedido: sem ela, três fornecedores cotam três
                // óleos diferentes e a comparação não compara nada.
                if (linha.getSpecification() == null) {
                    linha.setSpecification(p.getSpecification());
                }
            }
            if (l.assetId() != null && !l.assetId().isBlank()) {
                Asset a = assets.findByIdAndOrganizationId(l.assetId(), orgId)
                        .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
                linha.setAsset(a);
                linha.setAssetTag(a.getTag());
            }
            r.addLine(linha);
        }
        if (r.getLines().isEmpty()) {
            throw ApiException.badRequest("Um pedido sem linhas não pede nada.");
        }
    }

    private String proximoNumero(String orgId) {
        int ano = LocalDate.now(ZoneOffset.UTC).getYear();
        long n = counters.next(orgId, "rfq_" + ano);
        return String.format("PC-%d-%04d", ano, n);
    }

    // ==== Respostas =========================================================

    @Transactional
    public PedidoView registarResposta(
            String orgId, String userId, String id, String supplierId, RespostaRequest req) {
        Rfq r = exigir(orgId, id);
        if (r.getStatus() != Rfq.Status.OPEN) {
            throw ApiException.conflict("Este pedido já está fechado.");
        }
        RfqSupplier rs = convidados.findByRfqIdAndSupplierId(id, supplierId)
                .orElseThrow(() -> ApiException.notFound(
                        "Este fornecedor não está neste pedido."));

        rs.setRespondedAt(Instant.now());
        rs.setDeclined(Boolean.TRUE.equals(req.declined()));
        rs.setLeadTimeDays(req.leadTimeDays());
        rs.setValidUntil(req.validUntil());
        rs.setPaymentTerms(limpo(req.paymentTerms()));
        rs.setNotes(limpo(req.notes()));
        if (req.sourceUid() != null) {
            rs.setSourceFolder(limpo(req.sourceFolder()));
            rs.setSourceUid(req.sourceUid());
        }

        if (rs.isDeclined()) {
            precos.deleteAll(precos.findByRfqSupplierId(rs.getId()));
            rs.setTotal(null);
        } else {
            Map<String, RfqLine> porLinha = new HashMap<>();
            r.getLines().forEach(l -> porLinha.put(l.getId(), l));
            BigDecimal total = BigDecimal.ZERO;

            for (PrecoRequest p : req.precos() == null ? List.<PrecoRequest>of() : req.precos()) {
                RfqLine linha = porLinha.get(p.lineId());
                if (linha == null) {
                    throw ApiException.badRequest("Linha desconhecida neste pedido.");
                }
                RfqQuote q = precos.findByRfqSupplierIdAndRfqLineId(rs.getId(), linha.getId())
                        .orElseGet(() -> {
                            RfqQuote novo = new RfqQuote();
                            novo.setRfqSupplier(rs);
                            novo.setRfqLine(linha);
                            return novo;
                        });
                q.setUnavailable(Boolean.TRUE.equals(p.unavailable()));
                q.setUnitPrice(q.isUnavailable() ? null : p.unitPrice());
                q.setSupplierPartNumber(limpo(p.supplierPartNumber()));
                q.setNote(limpo(p.note()));
                precos.save(q);
                if (!q.isUnavailable() && q.getUnitPrice() != null) {
                    total = total.add(q.getUnitPrice().multiply(linha.getQuantity()));
                }
            }
            rs.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        }

        audit.record(orgId, userId, "rfq.response", "Rfq", r.getId(),
                r.getNumber() + " · " + rs.getSupplier().getName()
                        + (rs.isDeclined() ? " · não fornece" : " · " + rs.getTotal()));
        return ver(orgId, r);
    }

    @Transactional
    public PedidoView marcarEnviado(String orgId, String supplierId, String id, String para) {
        Rfq r = exigir(orgId, id);
        RfqSupplier rs = convidados.findByRfqIdAndSupplierId(id, supplierId)
                .orElseThrow(() -> ApiException.notFound("Este fornecedor não está neste pedido."));
        rs.setSentAt(Instant.now());
        rs.setSentTo(para);
        return ver(orgId, r);
    }

    /**
     * Os pedidos em aberto a que este fornecedor ainda pode responder.
     *
     * <p>Serve o correio: quando chega uma mensagem de um fornecedor, saber se
     * ele tem um pedido à espera é a diferença entre registar a proposta em
     * dois cliques e procurá-la num mês.
     */
    @Transactional(readOnly = true)
    public List<PedidoView> abertosParaFornecedor(String orgId, String supplierId) {
        return pedidos.findByEstado(orgId, Rfq.Status.OPEN).stream()
                .filter(r -> convidados.findByRfqIdAndSupplierId(r.getId(), supplierId).isPresent())
                .map(r -> ver(orgId, r))
                .toList();
    }

    /**
     * Guarda a proposta tal como ele a mandou.
     *
     * <p>Os bytes vêm de fora — de um ficheiro que alguém carrega, ou de um
     * anexo de uma mensagem do correio. Em qualquer dos casos o que interessa
     * é o mesmo: daqui a um mês, quando alguém perguntar «mas ele cotou mesmo
     * esse preço?», a resposta está ao lado do número e não enterrada em
     * quatrocentas mensagens.
     */
    @Transactional
    public PedidoView guardarProposta(
            String orgId, String userId, String id, String supplierId,
            String nome, String tipo, byte[] conteudo) {
        Rfq r = exigir(orgId, id);
        RfqSupplier rs = convidados.findByRfqIdAndSupplierId(id, supplierId)
                .orElseThrow(() -> ApiException.notFound("Este fornecedor não está neste pedido."));
        if (conteudo == null || conteudo.length == 0) {
            throw ApiException.badRequest("O ficheiro da proposta está vazio.");
        }

        String chave = armazenamento.store(conteudo, tipo, nome);
        ao.autocare.domain.StoredFile registo = new ao.autocare.domain.StoredFile();
        registo.setOrganization(organizations.getReferenceById(orgId));
        registo.setStorageKey(chave);
        registo.setOriginalName(nome);
        registo.setContentType(tipo);
        registo.setSizeBytes(conteudo.length);
        ficheiros.save(registo);

        rs.setFileId(registo.getId());
        rs.setFileName(nome);
        audit.record(orgId, userId, "rfq.proposal", "Rfq", r.getId(),
                r.getNumber() + " · " + rs.getSupplier().getName() + " · " + nome);
        return ver(orgId, r);
    }

    // ==== Adjudicar =========================================================

    /**
     * Escolher um fornecedor e transformar o pedido numa encomenda.
     *
     * <p>Se não se escolhe o mais barato, o motivo é obrigatório. Não é
     * burocracia: é o caso que interessa registar, e é o que distingue «escolhi
     * este porque entrega numa semana» de «escolhi este porque sim».
     *
     * <p>Os preços cotados ficam também na lista de preços do fornecedor, com
     * a validade que ele deu. Senão, daqui a um mês ninguém se lembra do que
     * ele cobrou e pede-se tudo outra vez.
     */
    @Transactional
    public PedidoView adjudicar(String orgId, String userId, String id, AdjudicarRequest req) {
        Rfq r = exigir(orgId, id);
        if (r.getStatus() != Rfq.Status.OPEN) {
            throw ApiException.conflict("Este pedido já está fechado.");
        }
        RfqSupplier escolhido = convidados.findByRfqIdAndSupplierId(id, req.supplierId())
                .orElseThrow(() -> ApiException.notFound("Este fornecedor não está neste pedido."));
        if (escolhido.getRespondedAt() == null || escolhido.isDeclined()) {
            throw ApiException.badRequest(
                    "Não se adjudica a quem não respondeu, ou a quem disse que não fornece.");
        }

        PedidoView comparacao = ver(orgId, r);
        PropostaView proposta = comparacao.propostas().stream()
                .filter(p -> p.supplierId().equals(req.supplierId()))
                .findFirst().orElseThrow();
        if (!proposta.maisBarata() && (req.motivo() == null || req.motivo().isBlank())) {
            PropostaView barata = comparacao.propostas().stream()
                    .filter(PropostaView::maisBarata).findFirst().orElse(null);
            throw ApiException.badRequest(
                    "Não está a escolher o mais barato"
                            + (barata == null ? "" : " (" + barata.supplierName() + ", "
                                    + barata.total() + " " + barata.currency() + ")")
                            + ". Diga porquê — pode ser a decisão certa, mas tem de ficar escrito.");
        }

        // A encomenda, com os preços que ele cotou.
        List<OrderDtos.LinhaRequest> linhasDaOrdem = new ArrayList<>();
        for (RfqLine l : r.getLines()) {
            RfqQuote q = precos.findByRfqSupplierIdAndRfqLineId(escolhido.getId(), l.getId())
                    .orElse(null);
            if (q == null || q.isUnavailable() || q.getUnitPrice() == null) {
                continue;
            }
            linhasDaOrdem.add(new OrderDtos.LinhaRequest(
                    l.getPart() != null ? l.getPart().getId() : null,
                    l.getDescription(), l.getSpecification(), q.getSupplierPartNumber(),
                    l.getQuantity(), l.getUnit(), q.getUnitPrice(),
                    l.getAsset() != null ? l.getAsset().getId() : null));
        }
        if (linhasDaOrdem.isEmpty()) {
            throw ApiException.badRequest(
                    "Este fornecedor não cotou nenhuma das linhas deste pedido.");
        }
        // A data de entrega é a que ele próprio prometeu na cotação. Sem ela, a
        // ordem nasce sem prazo e a pontualidade deste fornecedor nunca se
        // chega a medir — foi o que esta volta mostrou.
        LocalDate prometida = escolhido.getLeadTimeDays() != null
                ? LocalDate.now(ZoneOffset.UTC).plusDays(escolhido.getLeadTimeDays())
                : null;
        OrderDtos.OrdemView ordem = ordens.criar(orgId, userId, new OrderDtos.OrdemRequest(
                req.supplierId(), prometida, null,
                "Do pedido de cotação " + r.getNumber()
                        + (prometida != null
                                ? " · prazo prometido: " + escolhido.getLeadTimeDays() + " dias"
                                : ""),
                null, null, linhasDaOrdem));
        ordens.ligarAoPedido(orgId, ordem.id(), r.getId());

        // E o que ele cotou passa a ser o preço dele no sistema.
        guardarPrecosCotados(orgId, userId, escolhido, r);

        r.setStatus(Rfq.Status.AWARDED);
        r.setAwardedSupplier(escolhido.getSupplier());
        r.setAwardedOrderId(ordem.id());
        r.setAwardedReason(limpo(req.motivo()));
        r.setAwardedAt(Instant.now());

        audit.record(orgId, userId, "rfq.award", "Rfq", r.getId(),
                r.getNumber() + " → " + escolhido.getSupplier().getName()
                        + " · ordem " + ordem.number()
                        + (req.motivo() != null ? " · " + req.motivo() : ""));
        return ver(orgId, r);
    }

    private void guardarPrecosCotados(
            String orgId, String userId, RfqSupplier escolhido, Rfq r) {
        for (RfqLine l : r.getLines()) {
            if (l.getPart() == null) {
                continue;
            }
            RfqQuote q = precos.findByRfqSupplierIdAndRfqLineId(escolhido.getId(), l.getId())
                    .orElse(null);
            if (q == null || q.isUnavailable() || q.getUnitPrice() == null) {
                continue;
            }
            try {
                caderno.guardarPreco(orgId, userId, escolhido.getSupplier().getId(),
                        new SavePriceRequest(
                                l.getPart().getId(), q.getSupplierPartNumber(), q.getUnitPrice(),
                                escolhido.getCurrency(), null, escolhido.getLeadTimeDays(),
                                LocalDate.now(ZoneOffset.UTC), escolhido.getValidUntil(),
                                "Do pedido " + r.getNumber()));
            } catch (RuntimeException e) {
                // Guardar o preço é um extra: não pode fazer cair a adjudicação.
                audit.record(orgId, userId, "rfq.price_save_failed", "Rfq", r.getId(),
                        e.getMessage());
            }
        }
    }

    @Transactional
    public PedidoView anular(String orgId, String userId, String id, String motivo) {
        Rfq r = exigir(orgId, id);
        if (r.getStatus() == Rfq.Status.AWARDED) {
            throw ApiException.conflict("Um pedido já adjudicado não se anula.");
        }
        r.setStatus(Rfq.Status.CANCELLED);
        r.setAwardedReason(limpo(motivo));
        audit.record(orgId, userId, "rfq.cancel", "Rfq", r.getId(), r.getNumber());
        return ver(orgId, r);
    }

    // ==== Leitura e comparação ==============================================

    @Transactional(readOnly = true)
    public List<PedidoView> listar(String orgId, String estado) {
        List<Rfq> lista = estado == null || estado.isBlank()
                ? pedidos.findAllForOrg(orgId)
                : pedidos.findByEstado(orgId, Rfq.Status.valueOf(estado));
        return lista.stream().map(r -> ver(orgId, r)).toList();
    }

    @Transactional(readOnly = true)
    public PedidoView obter(String orgId, String id) {
        return ver(orgId, exigir(orgId, id));
    }

    /**
     * A comparação.
     *
     * <p>Marca o mais barato e o mais rápido, e diz por quanto é que cada preço
     * está acima do melhor daquela linha — que é onde se vê que um fornecedor
     * é barato no filtro e caro no óleo. E escreve a leitura em português,
     * porque é essa frase que falta a quem decide e que nunca aparece num
     * quadro de números.
     */
    private PedidoView ver(String orgId, Rfq r) {
        List<RfqSupplier> lista = convidados.findByRfq(r.getId());
        List<RfqQuote> todos = lista.isEmpty()
                ? List.of()
                : precos.findByFornecedores(lista.stream().map(RfqSupplier::getId).toList());

        Map<String, Map<String, RfqQuote>> porFornecedor = new HashMap<>();
        for (RfqQuote q : todos) {
            porFornecedor
                    .computeIfAbsent(q.getRfqSupplier().getId(), k -> new HashMap<>())
                    .put(q.getRfqLine().getId(), q);
        }

        // O melhor preço de cada linha, para se ver quem está acima e quanto.
        Map<String, BigDecimal> melhorDaLinha = new HashMap<>();
        for (RfqQuote q : todos) {
            if (q.isUnavailable() || q.getUnitPrice() == null) {
                continue;
            }
            melhorDaLinha.merge(q.getRfqLine().getId(), q.getUnitPrice(), BigDecimal::min);
        }

        BigDecimal menorTotal = null;
        Integer menorPrazo = null;
        for (RfqSupplier s : lista) {
            if (s.getRespondedAt() == null || s.isDeclined() || s.getTotal() == null) {
                continue;
            }
            menorTotal = menorTotal == null ? s.getTotal() : menorTotal.min(s.getTotal());
            if (s.getLeadTimeDays() != null) {
                menorPrazo = menorPrazo == null
                        ? s.getLeadTimeDays() : Math.min(menorPrazo, s.getLeadTimeDays());
            }
        }

        List<PropostaView> propostas = new ArrayList<>();
        for (RfqSupplier s : lista) {
            Map<String, RfqQuote> seus = porFornecedor.getOrDefault(s.getId(), Map.of());
            List<PrecoView> linhas = new ArrayList<>();
            int cotadas = 0;
            for (RfqLine l : r.getLines()) {
                RfqQuote q = seus.get(l.getId());
                if (q == null) {
                    linhas.add(new PrecoView(l.getId(), null, null, null, false, null, null, false));
                    continue;
                }
                boolean temPreco = !q.isUnavailable() && q.getUnitPrice() != null;
                if (temPreco) {
                    cotadas++;
                }
                BigDecimal melhor = melhorDaLinha.get(l.getId());
                Integer acima = null;
                if (temPreco && melhor != null && melhor.signum() > 0) {
                    acima = q.getUnitPrice().subtract(melhor).multiply(new BigDecimal("100"))
                            .divide(melhor, 0, RoundingMode.HALF_UP).intValue();
                }
                linhas.add(new PrecoView(
                        l.getId(), q.getUnitPrice(),
                        temPreco ? q.getUnitPrice().multiply(l.getQuantity())
                                .setScale(2, RoundingMode.HALF_UP) : null,
                        q.getSupplierPartNumber(), q.isUnavailable(), q.getNote(),
                        acima, temPreco && acima != null && acima == 0));
            }

            propostas.add(new PropostaView(
                    s.getId(), s.getSupplier().getId(), s.getSupplier().getName(),
                    s.getSentAt(), s.getSentTo(), s.getRespondedAt(), s.isDeclined(),
                    s.getLeadTimeDays(), s.getValidUntil(), s.getPaymentTerms(), s.getNotes(),
                    s.getTotal(), s.getCurrency(),
                    cotadas, cotadas == r.getLines().size(),
                    s.getTotal() != null && s.getTotal().equals(menorTotal),
                    s.getLeadTimeDays() != null && s.getLeadTimeDays().equals(menorPrazo),
                    s.getFileId(), s.getFileName(),
                    s.getSourceFolder(), s.getSourceUid(),
                    linhas));
        }

        List<LinhaView> linhas = r.getLines().stream()
                .map(l -> new LinhaView(l.getId(),
                        l.getPart() != null ? l.getPart().getId() : null,
                        l.getDescription(), l.getSpecification(), l.getQuantity(),
                        l.getUnit(), l.getAssetTag()))
                .toList();

        BigDecimal maiorTotal = propostas.stream()
                .map(PropostaView::total).filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null);
        BigDecimal diferenca = menorTotal != null && maiorTotal != null
                ? maiorTotal.subtract(menorTotal) : null;

        return new PedidoView(
                r.getId(), r.getNumber(), r.getTitle(), r.getStatus().name(), r.getStatus().label(),
                r.getDueDate(), r.getNotes(), r.getCreatedByLabel(), r.getCreatedAt(),
                r.getAwardedOrderId(),
                r.getAwardedSupplier() != null ? r.getAwardedSupplier().getId() : null,
                r.getAwardedSupplier() != null ? r.getAwardedSupplier().getName() : null,
                r.getAwardedReason(), r.getAwardedAt(),
                linhas, propostas, leitura(propostas, diferenca), diferenca);
    }

    /** A frase que falta a quem decide. */
    private static String leitura(List<PropostaView> propostas, BigDecimal diferenca) {
        List<PropostaView> comResposta = propostas.stream()
                .filter(p -> p.respondedAt() != null && !p.declined() && p.total() != null)
                .toList();
        if (comResposta.isEmpty()) {
            long porResponder = propostas.stream()
                    .filter(p -> p.respondedAt() == null).count();
            return porResponder > 0
                    ? "Ainda não há respostas: " + porResponder + " fornecedor(es) por responder."
                    : "Ninguém respondeu a este pedido.";
        }
        PropostaView barata = comResposta.stream().filter(PropostaView::maisBarata)
                .findFirst().orElse(comResposta.get(0));
        PropostaView rapida = comResposta.stream().filter(PropostaView::maisRapida)
                .findFirst().orElse(null);

        StringBuilder sb = new StringBuilder("O mais barato é ").append(barata.supplierName());
        if (diferenca != null && diferenca.signum() > 0) {
            sb.append(", e a diferença para o mais caro é de ")
                    .append(diferenca.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(' ').append(barata.currency());
        }
        sb.append('.');
        if (rapida != null && !rapida.supplierId().equals(barata.supplierId())) {
            sb.append(" Mas ").append(rapida.supplierName()).append(" entrega em ")
                    .append(rapida.leadTimeDays()).append(" dias");
            if (barata.leadTimeDays() != null) {
                sb.append(" contra ").append(barata.leadTimeDays());
            }
            sb.append(" — se o equipamento está parado, a conta é outra.");
        }
        long incompletas = comResposta.stream().filter(p -> !p.completa()).count();
        if (incompletas > 0) {
            sb.append(' ').append(incompletas)
                    .append(" proposta(s) não cobrem todas as linhas.");
        }
        return sb.toString();
    }

    /** Para onde vai o pedido: o contacto principal, ou o email da ficha. */
    @Transactional(readOnly = true)
    public String emailDoFornecedor(String orgId, String supplierId) {
        var contacto = caderno.destinatario(supplierId);
        if (contacto != null && contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
            return contacto.getEmail();
        }
        return suppliers.findByIdAndOrganizationId(supplierId, orgId)
                .map(Supplier::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
    }

    // ==== Utilitários =======================================================

    private Rfq exigir(String orgId, String id) {
        return pedidos.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Pedido de cotação não encontrado."));
    }

    private String nomeDe(String userId) {
        return userId == null ? null : users.findById(userId).map(u -> u.getName()).orElse(null);
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
