package ao.autocare.modules.part;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Part;
import ao.autocare.domain.PartApplication;
import ao.autocare.domain.PartCrossRef;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.part.dto.FitmentDtos.Achado;
import ao.autocare.modules.part.dto.FitmentDtos.ApplicationView;
import ao.autocare.modules.part.dto.FitmentDtos.Compatibilidade;
import ao.autocare.modules.part.dto.FitmentDtos.CrossRefView;
import ao.autocare.modules.part.dto.FitmentDtos.PecaCompativel;
import ao.autocare.modules.part.dto.FitmentDtos.SaveApplicationRequest;
import ao.autocare.modules.part.dto.FitmentDtos.SaveCrossRefRequest;
import ao.autocare.modules.plan.PlanCatalog;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PartApplicationRepository;
import ao.autocare.repo.PartCrossRefRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.repo.StockItemRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que peça serve que equipamento — e o que a substitui.
 *
 * <p>Um armazém que só sabe os nomes das peças deixa comprar e montar a peça
 * errada. «Filtro de óleo» é o nome de cinquenta peças diferentes, e a que
 * serve o Hino não serve o Hilux. A diferença entre um sistema de inventário e
 * um catálogo de peças é esta: o catálogo sabe <b>onde</b> cada peça se monta.
 *
 * <p>O modelo é o dos catálogos da indústria, simplificado para uma frota
 * angolana: cada peça tem uma ou mais <i>aplicações</i> (família, marca,
 * modelo, motor, intervalo de anos — cada campo vazio significa «qualquer») e
 * serve o equipamento se qualquer uma delas encaixar. E tem
 * <i>referências cruzadas</i>, porque o que chega ao fornecedor raramente é o
 * número original.
 *
 * <p>Há quatro veredictos e não dois, de propósito. Quando falta o motor ou o
 * ano do equipamento, a resposta certa é «não se pode confirmar» — e não
 * «serve». Um sistema que transforma falta de dados em certeza é um sistema
 * que um dia manda montar a peça errada com a sua bênção.
 */
@Service
public class FitmentService {

    /** O equipamento reduzido ao que decide uma peça. */
    public record Alvo(
            String assetId, String tag, String family,
            String make, String model, String engine, Integer year) {

        public static Alvo de(Asset a) {
            String familia = PlanCatalog.codigoPara(
                    a.getAssetType() != null && a.getAssetType().getCategory() != null
                            ? a.getAssetType().getCategory().name() : null,
                    a.getAssetType() != null ? a.getAssetType().getName() : null,
                    a.getManufacturer(), a.getModel(), a.getModelYear());
            return new Alvo(a.getId(), a.getTag(), familia, a.getManufacturer(), a.getModel(),
                    a.getEngine(), a.getModelYear());
        }
    }

    private final PartRepository parts;
    private final PartApplicationRepository applications;
    private final PartCrossRefRepository crossRefs;
    private final StockItemRepository stock;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public FitmentService(
            PartRepository parts,
            PartApplicationRepository applications,
            PartCrossRefRepository crossRefs,
            StockItemRepository stock,
            AssetRepository assets,
            OrganizationRepository organizations,
            AuditService audit) {
        this.parts = parts;
        this.applications = applications;
        this.crossRefs = crossRefs;
        this.stock = stock;
        this.assets = assets;
        this.organizations = organizations;
        this.audit = audit;
    }

    // ==== A regra: esta peça serve este equipamento? ========================

    /**
     * O veredicto de uma peça para um equipamento.
     *
     * <p>Basta uma aplicação encaixar. Se nenhuma encaixa mas alguma só falhou
     * por falta de um dado do equipamento, o veredicto é «incerto» — e diz-se
     * qual é o dado que falta, para alguém o poder preencher.
     */
    public static Compatibilidade avaliar(List<PartApplication> aplicacoes, Alvo alvo) {
        if (aplicacoes == null || aplicacoes.isEmpty()) {
            return new Compatibilidade(Compatibilidade.SEM_INFORMACAO,
                    "Esta peça não tem compatibilidade declarada: ninguém disse ainda "
                            + "em que equipamentos se monta.");
        }
        String faltaDado = null;
        for (PartApplication a : aplicacoes) {
            String r = encaixa(a, alvo);
            if (r == null) {
                return new Compatibilidade(Compatibilidade.SERVE,
                        a.universal() ? "Indicada para qualquer equipamento"
                                : "Indicada para " + ApplicationView.of(a).resumo());
            }
            if (!r.isEmpty() && faltaDado == null) {
                faltaDado = r;
            }
        }
        if (faltaDado != null) {
            return new Compatibilidade(Compatibilidade.INCERTO, faltaDado);
        }
        List<String> onde = aplicacoes.stream().limit(3)
                .map(a -> ApplicationView.of(a).resumo()).toList();
        return new Compatibilidade(Compatibilidade.NAO_SERVE,
                "Esta peça está indicada para " + String.join("; ", onde)
                        + " — não para este equipamento.");
    }

    /**
     * Confronta uma aplicação com um equipamento.
     *
     * @return {@code null} quando encaixa; o texto do dado que falta quando não
     *     se pode concluir; uma cadeia vazia quando simplesmente não serve
     */
    private static String encaixa(PartApplication a, Alvo alvo) {
        if (preenchido(a.getFamily()) && !igual(a.getFamily(), alvo.family())) {
            return "";
        }
        if (preenchido(a.getMake())) {
            if (!preenchido(alvo.make())) {
                return "Falta a marca do equipamento para confirmar a compatibilidade.";
            }
            if (!cruza(a.getMake(), alvo.make())) {
                return "";
            }
        }
        if (preenchido(a.getModel())) {
            if (!preenchido(alvo.model())) {
                return "Falta o modelo do equipamento para confirmar a compatibilidade.";
            }
            if (!cruza(a.getModel(), alvo.model())) {
                return "";
            }
        }
        if (preenchido(a.getEngine())) {
            // O motor costuma vir escrito no modelo («Hilux 2.4 D-4D»): vale como
            // confirmação. O que não se pode é dar por confirmado quando não há
            // nem campo de motor nem rasto dele no modelo.
            String texto = (alvo.engine() == null ? "" : alvo.engine() + " ")
                    + (alvo.model() == null ? "" : alvo.model());
            if (!preenchido(texto)) {
                return "Falta o motor do equipamento para confirmar a compatibilidade.";
            }
            if (!cruza(a.getEngine(), texto)) {
                return preenchido(alvo.engine())
                        ? ""
                        : "O equipamento não tem o motor registado, e esta peça é "
                                + "específica do motor " + a.getEngine() + ".";
            }
        }
        if (a.getYearFrom() != null || a.getYearTo() != null) {
            if (alvo.year() == null) {
                return "Falta o ano de fabrico do equipamento para confirmar a compatibilidade.";
            }
            if (a.getYearFrom() != null && alvo.year() < a.getYearFrom()) {
                return "";
            }
            if (a.getYearTo() != null && alvo.year() > a.getYearTo()) {
                return "";
            }
        }
        return null;
    }

    // ==== As peças que servem um equipamento ================================

    /**
     * O catálogo filtrado por este equipamento.
     *
     * <p>É o ecrã que o mecânico quer: «o que é que serve este camião, e há em
     * armazém?». As peças sem compatibilidade declarada ficam de fora por
     * omissão — mostrá-las seria mostrar o armazém inteiro e não responder a
     * nada —, mas diz-se quantas são, porque é trabalho que falta fazer.
     */
    @Transactional(readOnly = true)
    public Resultado compativeis(String orgId, String assetId, String categoria, boolean incluirSemInfo) {
        Asset asset = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
        Alvo alvo = Alvo.de(asset);

        List<Part> todas = parts.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(Part::isActive)
                .filter(p -> categoria == null || categoria.isBlank()
                        || p.getCategory().name().equalsIgnoreCase(categoria))
                .toList();
        if (todas.isEmpty()) {
            return new Resultado(alvo, List.of(), 0);
        }

        Map<String, List<PartApplication>> porPeca = new HashMap<>();
        for (PartApplication a : applications.findByPartIds(todas.stream().map(Part::getId).toList())) {
            porPeca.computeIfAbsent(a.getPart().getId(), k -> new ArrayList<>()).add(a);
        }
        Map<String, BigDecimal> existencias = new HashMap<>();
        for (Object[] r : stock.totalsByPartForOrg(orgId)) {
            existencias.put((String) r[0], (BigDecimal) r[1]);
        }

        List<PecaCompativel> linhas = new ArrayList<>();
        int semInfo = 0;
        for (Part p : todas) {
            Compatibilidade c = avaliar(porPeca.get(p.getId()), alvo);
            if (Compatibilidade.SEM_INFORMACAO.equals(c.veredicto())) {
                semInfo++;
                if (!incluirSemInfo) {
                    continue;
                }
            } else if (Compatibilidade.NAO_SERVE.equals(c.veredicto())) {
                continue;
            }
            BigDecimal existe = existencias.getOrDefault(p.getId(), BigDecimal.ZERO);
            linhas.add(new PecaCompativel(
                    p.getId(), p.getName(), p.getPartNumber(), p.getSpecification(),
                    p.getCategory().name(), p.getSystemCode(), p.getUnit(),
                    existe, p.getMinQuantity(),
                    p.getMinQuantity() != null && p.getMinQuantity().signum() > 0
                            && existe.compareTo(p.getMinQuantity()) <= 0,
                    p.getLeadTimeDays(), c.veredicto(), c.motivo(),
                    crossRefs.findByPartIdOrderByKindAscNumberAsc(p.getId()).stream()
                            .map(CrossRefView::of).toList()));
        }
        return new Resultado(alvo, linhas, semInfo);
    }

    /** O que serve este equipamento, e quanto do armazém ainda ninguém classificou. */
    public record Resultado(Alvo equipamento, List<PecaCompativel> pecas, int semCompatibilidade) {}

    // ==== Procurar pelo número que vem na caixa =============================

    /**
     * Encontra o artigo por qualquer número: o dele, o original ou o de um
     * equivalente.
     *
     * <p>Quem está no balcão tem uma caixa na mão e um número impresso nela.
     * Esse número pode ser de qualquer dos cinco fabricantes que fazem a mesma
     * peça — e, sem isto, a procura falha e compra-se outra vez o que já há.
     */
    @Transactional(readOnly = true)
    public List<Achado> procurar(String orgId, String numero) {
        if (numero == null || numero.isBlank()) {
            return List.of();
        }
        String limpo = numero.trim();
        String padrao = "%" + limpo + "%";
        Map<String, BigDecimal> existencias = new HashMap<>();
        for (Object[] r : stock.totalsByPartForOrg(orgId)) {
            existencias.put((String) r[0], (BigDecimal) r[1]);
        }

        Map<String, Achado> achados = new java.util.LinkedHashMap<>();
        for (Part p : parts.findByOrganizationIdOrderByNameAsc(orgId)) {
            if (p.getPartNumber() != null
                    && p.getPartNumber().toUpperCase(Locale.ROOT).contains(limpo.toUpperCase(Locale.ROOT))) {
                achados.put(p.getId(), achado(p, existencias, "referência do artigo", p.getPartNumber()));
            }
        }
        for (PartCrossRef r : crossRefs.searchByNumber(orgId, padrao)) {
            Part p = r.getPart();
            if (achados.containsKey(p.getId())) {
                continue;
            }
            String como = CrossRefView.of(r).kindLabel()
                    + (r.getBrand() != null && !r.getBrand().isBlank() ? " " + r.getBrand() : "");
            achados.put(p.getId(), achado(p, existencias, como, r.getNumber()));
        }
        return List.copyOf(achados.values());
    }

    private static Achado achado(
            Part p, Map<String, BigDecimal> existencias, String como, String numero) {
        return new Achado(p.getId(), p.getName(), p.getPartNumber(), p.getSpecification(),
                p.getCategory().name(), existencias.getOrDefault(p.getId(), BigDecimal.ZERO),
                como, numero);
    }

    // ==== Aplicações: criar, listar, apagar =================================

    @Transactional(readOnly = true)
    public List<ApplicationView> listarAplicacoes(String orgId, String partId) {
        requirePart(orgId, partId);
        return applications.findByPartIdOrderByCreatedAtAsc(partId).stream()
                .map(ApplicationView::of).toList();
    }

    @Transactional
    public ApplicationView criarAplicacao(
            String orgId, String userId, String partId, SaveApplicationRequest req) {
        Part p = requirePart(orgId, partId);
        if (req.yearFrom() != null && req.yearTo() != null && req.yearFrom() > req.yearTo()) {
            throw ApiException.badRequest("O ano inicial não pode ser maior do que o final.");
        }
        PartApplication a = new PartApplication();
        a.setOrganization(organizations.getReferenceById(orgId));
        a.setPart(p);
        a.setFamily(limpo(req.family()));
        a.setMake(limpo(req.make()));
        a.setModel(limpo(req.model()));
        a.setEngine(limpo(req.engine()));
        a.setYearFrom(req.yearFrom());
        a.setYearTo(req.yearTo());
        a.setNote(limpo(req.note()));
        applications.save(a);
        audit.record(orgId, userId, "part.application.add", "Part", partId,
                p.getName() + " → " + ApplicationView.of(a).resumo());
        return ApplicationView.of(a);
    }

    /**
     * «Esta peça serve este equipamento» — num clique.
     *
     * <p>É como a informação entra de verdade: quem acabou de montar a peça sabe
     * em que viatura a montou. Pedir-lhe que escreva marca, modelo, motor e
     * anos num formulário é garantir que ninguém preenche nada; copiar os dados
     * do equipamento é um botão.
     */
    @Transactional
    public ApplicationView aplicacaoDoEquipamento(
            String orgId, String userId, String partId, String assetId, boolean soAFamilia) {
        Asset asset = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
        Alvo alvo = Alvo.de(asset);
        SaveApplicationRequest req = soAFamilia
                ? new SaveApplicationRequest(alvo.family(), null, null, null, null, null,
                        "Criada a partir de " + asset.getTag())
                : new SaveApplicationRequest(alvo.family(), alvo.make(), alvo.model(),
                        alvo.engine(), alvo.year(), alvo.year(),
                        "Criada a partir de " + asset.getTag());
        return criarAplicacao(orgId, userId, partId, req);
    }

    /**
     * Marca um conjunto de artigos como sendo de uma família.
     *
     * <p>É o que faz a compatibilidade existir sem ninguém a escrever. Quando
     * se aplica o plano de camiões pesados, o sistema cria no armazém os
     * filtros e as correias desse plano — e a partir de agora marca-os também
     * como sendo de camiões pesados. No dia em que esse mesmo filtro aparecer
     * na lista de uma pick-up, o painel avisa.
     *
     * <p>É idempotente: aplicar o plano a dez camiões não cria dez linhas
     * iguais. E não apaga nada: um artigo que sirva camiões e autocarros
     * acaba com as duas famílias declaradas, que é a verdade.
     *
     * @return quantas aplicações foram criadas
     */
    @Transactional
    public int declararFamilia(String orgId, String userId, String familia, List<String> nomes) {
        if (familia == null || familia.isBlank() || nomes == null || nomes.isEmpty()) {
            return 0;
        }
        Map<String, Part> porNome = new HashMap<>();
        for (Part p : parts.findByOrganizationIdOrderByNameAsc(orgId)) {
            porNome.putIfAbsent(chave(p.getName()), p);
        }
        Map<String, List<PartApplication>> jaDeclaradas = new HashMap<>();
        for (PartApplication a : applications.findAllForOrg(orgId)) {
            jaDeclaradas.computeIfAbsent(a.getPart().getId(), k -> new ArrayList<>()).add(a);
        }

        int criadas = 0;
        for (String nome : nomes) {
            Part p = porNome.get(chave(nome));
            if (p == null) {
                continue;
            }
            boolean jaTem = jaDeclaradas.getOrDefault(p.getId(), List.of()).stream()
                    .anyMatch(a -> igual(familia, a.getFamily()));
            if (jaTem) {
                continue;
            }
            PartApplication a = new PartApplication();
            a.setOrganization(organizations.getReferenceById(orgId));
            a.setPart(p);
            a.setFamily(familia);
            a.setNote("Do catálogo de manutenção");
            applications.save(a);
            jaDeclaradas.computeIfAbsent(p.getId(), k -> new ArrayList<>()).add(a);
            criadas++;
        }
        if (criadas > 0) {
            audit.record(orgId, userId, "part.application.catalog", "Organization", orgId,
                    criadas + " artigo(s) marcados como " + familia);
        }
        return criadas;
    }

    @Transactional
    public void apagarAplicacao(String orgId, String userId, String id) {
        PartApplication a = applications.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Aplicação não encontrada."));
        String nome = a.getPart().getName();
        applications.delete(a);
        audit.record(orgId, userId, "part.application.remove", "Part", a.getPart().getId(), nome);
    }

    // ==== Referências cruzadas ==============================================

    @Transactional(readOnly = true)
    public List<CrossRefView> listarReferencias(String orgId, String partId) {
        requirePart(orgId, partId);
        return crossRefs.findByPartIdOrderByKindAscNumberAsc(partId).stream()
                .map(CrossRefView::of).toList();
    }

    @Transactional
    public CrossRefView criarReferencia(
            String orgId, String userId, String partId, SaveCrossRefRequest req) {
        Part p = requirePart(orgId, partId);
        String numero = req.number().trim();
        if (crossRefs.existsByPartIdAndNumberIgnoreCase(partId, numero)) {
            throw ApiException.conflict("Esta referência já está registada nesta peça.");
        }
        PartCrossRef.Kind kind;
        try {
            kind = req.kind() == null || req.kind().isBlank()
                    ? PartCrossRef.Kind.EQUIVALENT
                    : PartCrossRef.Kind.valueOf(req.kind().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Tipo de referência desconhecido: " + req.kind());
        }
        PartCrossRef r = new PartCrossRef();
        r.setOrganization(organizations.getReferenceById(orgId));
        r.setPart(p);
        r.setNumber(numero);
        r.setBrand(limpo(req.brand()));
        r.setKind(kind);
        r.setNote(limpo(req.note()));
        crossRefs.save(r);
        audit.record(orgId, userId, "part.crossref.add", "Part", partId,
                p.getName() + " ← " + numero);
        return CrossRefView.of(r);
    }

    @Transactional
    public void apagarReferencia(String orgId, String userId, String id) {
        PartCrossRef r = crossRefs.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Referência não encontrada."));
        String nome = r.getPart().getName() + " ← " + r.getNumber();
        crossRefs.delete(r);
        audit.record(orgId, userId, "part.crossref.remove", "Part", r.getPart().getId(), nome);
    }

    // ==== Utilitários =======================================================

    private Part requirePart(String orgId, String partId) {
        return parts.findByIdAndOrganizationId(partId, orgId)
                .orElseThrow(() -> ApiException.notFound("Peça não encontrada."));
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean preenchido(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean igual(String a, String b) {
        return chave(a).equals(chave(b));
    }

    /**
     * «Toyota» encontra «TOYOTA MOTOR»; «Hilux» encontra «Hilux 2.4 D-4D».
     *
     * <p>Comparar ao contrário também: o armazém escreve «Hilux Cabina Dupla» e
     * a ficha da viatura diz «Hilux». Nenhum dos dois está errado.
     */
    private static boolean cruza(String a, String b) {
        String x = chave(a);
        String y = chave(b);
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }

    private static String chave(String s) {
        if (s == null) {
            return "";
        }
        String sem = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
