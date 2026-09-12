package ao.autocare.modules.importer;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetType;
import ao.autocare.domain.Location;
import ao.autocare.domain.Part;
import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.domain.enums.Enums.LocationKind;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PartCategory;
import ao.autocare.modules.asset.AssetService;
import ao.autocare.modules.asset.dto.AssetDtos.CreateAssetRequest;
import ao.autocare.modules.assettype.AssetTypeService;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.CreateAssetTypeRequest;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.importer.dto.ImportDtos.ImportReport;
import ao.autocare.modules.importer.dto.ImportDtos.RowError;
import ao.autocare.modules.location.LocationService;
import ao.autocare.modules.location.dto.LocationDtos.CreateLocationRequest;
import ao.autocare.modules.part.StockService;
import ao.autocare.modules.part.dto.PartDtos.SavePartRequest;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.domain.enums.Enums.FuelType;
import ao.autocare.modules.fuel.FuelService;
import ao.autocare.modules.fuel.dto.FuelDtos.SaveFuelRecordRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação em massa a partir de folhas de cálculo.
 *
 * <p>Uma empresa com trezentas máquinas não as cadastra à mão. O que decide se
 * uma importação é utilizável não é ler o ficheiro — é o que acontece quando
 * ele tem erros, que é sempre:
 *
 * <ul>
 *   <li><b>Verificação sem gravar</b> ({@code dryRun}): ver o que vai acontecer
 *       antes de acontecer. Numa folha de 300 linhas é a diferença entre
 *       corrigir três células e ter de limpar a base de dados à mão.</li>
 *   <li><b>Uma linha má não trava as boas</b>: o erro é reportado com o número
 *       da linha do ficheiro e as restantes entram.</li>
 *   <li><b>Reimportar atualiza</b> em vez de duplicar, usando a etiqueta do
 *       ativo (ou o nome da peça) como identidade. Ninguém acerta à primeira.</li>
 * </ul>
 */
@Service
public class ImportService {

    /** Nomes aceites para cada coluna. Ver {@code Csv.Row#getAny}. */
    private static final String[] COL_TAG = {"tag", "etiqueta", "codigo", "referencia"};
    private static final String[] COL_NAME = {"nome", "designacao", "descricao", "name"};
    private static final String[] COL_TYPE = {"tipo", "tipo de ativo", "categoria", "type"};
    private static final String[] COL_LOCATION = {"local", "localizacao", "obra", "location"};
    private static final String[] COL_MANUFACTURER = {"fabricante", "marca", "manufacturer"};
    private static final String[] COL_MODEL = {"modelo", "model"};
    private static final String[] COL_SERIAL =
            {"no serie", "numero de serie", "numero serie", "serie", "serial"};
    private static final String[] COL_YEAR = {"ano", "ano de fabrico", "ano de fabricacao", "year"};
    private static final String[] COL_PLATE = {"matricula", "placa", "plate"};
    private static final String[] COL_RESPONSIBLE = {"responsavel", "operador", "responsible"};
    private static final String[] COL_METER =
            {"horimetro", "horimetro inicial", "hodometro", "km", "leitura inicial"};
    private static final String[] COL_SPEED = {"limite de velocidade", "limite velocidade", "velocidade maxima"};
    private static final String[] COL_DOWNTIME =
            {"custo por hora parada", "custo hora parada", "custo paragem"};
    private static final String[] COL_TANK =
            {"capacidade do deposito", "capacidade deposito", "deposito litros", "tanque"};
    private static final String[] COL_NOTES = {"observacoes", "notas", "notes"};

    private static final String[] COL_FUEL_ASSET =
            {"ativo", "viatura", "tag", "etiqueta", "matricula", "asset"};
    private static final String[] COL_FUEL_DATE =
            {"data", "data do abastecimento", "quando", "date"};
    private static final String[] COL_FUEL_LITERS = {"litros", "quantidade", "liters", "qtd"};
    private static final String[] COL_FUEL_PRICE =
            {"preco por litro", "preco litro", "preco/l", "price"};
    private static final String[] COL_FUEL_TOTAL = {"total", "valor", "custo", "montante"};
    private static final String[] COL_FUEL_METER =
            {"contador", "hodometro", "odometro", "horimetro", "km", "meter"};
    private static final String[] COL_FUEL_FULL =
            {"deposito cheio", "cheio", "atestou", "full"};
    private static final String[] COL_FUEL_STATION =
            {"posto", "estacao", "bomba", "station"};
    private static final String[] COL_FUEL_DRIVER = {"motorista", "condutor", "driver"};
    private static final String[] COL_FUEL_CARD = {"cartao", "numero do cartao", "card"};
    private static final String[] COL_FUEL_INVOICE =
            {"fatura", "factura", "numero da fatura", "invoice"};
    private static final String[] COL_FUEL_TYPE =
            {"combustivel", "tipo de combustivel", "fuel"};

    private static final String[] COL_PART_NUMBER = {"numero de peca", "numero peca", "referencia", "partnumber", "sku"};
    private static final String[] COL_SYSTEM = {"sistema", "system"};
    private static final String[] COL_UNIT = {"unidade", "un", "unit"};
    private static final String[] COL_MIN_QTY = {"stock minimo", "minimo", "quantidade minima"};
    private static final String[] COL_COST = {"custo", "custo medio", "preco", "cost"};
    private static final String[] COL_CURRENCY = {"moeda", "currency"};

    /** Limite por ficheiro. Acima disto o pedido HTTP deixa de ser o meio certo. */
    private static final int MAX_ROWS = 5000;

    private final AssetRepository assets;
    private final AssetTypeRepository assetTypes;
    private final LocationRepository locations;
    private final PartRepository parts;
    private final AssetService assetService;
    private final FuelService fuelService;
    private final AssetTypeService assetTypeService;
    private final LocationService locationService;
    private final StockService stockService;
    private final AuditService audit;

    public ImportService(
            AssetRepository assets,
            AssetTypeRepository assetTypes,
            LocationRepository locations,
            PartRepository parts,
            AssetService assetService,
            AssetTypeService assetTypeService,
            LocationService locationService,
            StockService stockService,
            AuditService audit,
            FuelService fuelService) {
        this.assets = assets;
        this.assetTypes = assetTypes;
        this.locations = locations;
        this.parts = parts;
        this.assetService = assetService;
        this.fuelService = fuelService;
        this.assetTypeService = assetTypeService;
        this.locationService = locationService;
        this.stockService = stockService;
        this.audit = audit;
    }

    // ==== Ativos ========================================================
    @Transactional
    public ImportReport importAssets(String orgId, String userId, String content, boolean dryRun) {
        Csv.Sheet sheet = read(content);

        Map<String, AssetType> typesByName = index(
                assetTypes.findByOrganizationIdOrderByNameAsc(orgId), AssetType::getName);
        Map<String, Location> locationsByName = index(
                locations.findByOrganizationIdOrderByNameAsc(orgId), Location::getName);
        Map<String, Asset> assetsByTag = index(
                assets.findByOrganizationId(orgId), Asset::getTag);

        Set<String> createdReferences = new LinkedHashSet<>();
        List<RowError> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (Csv.Row row : sheet.rows()) {
            String tag = row.getAny(COL_TAG);
            try {
                if (tag == null) {
                    throw new IllegalArgumentException(
                            "Falta a etiqueta do ativo (coluna \"tag\").");
                }
                String name = row.getAny(COL_NAME);
                if (name == null) {
                    throw new IllegalArgumentException("Falta o nome do ativo.");
                }

                AssetType type = resolveType(orgId, userId, row, typesByName,
                        createdReferences, dryRun);
                Location location = resolveLocation(orgId, userId, row, locationsByName,
                        createdReferences, dryRun);

                Asset existing = assetsByTag.get(key(tag));
                if (existing != null) {
                    if (!dryRun) {
                        applyToExisting(existing, row, type, location);
                    }
                    updated++;
                    continue;
                }
                if (!dryRun) {
                    if (type == null) {
                        throw new IllegalArgumentException(
                                "Falta o tipo de ativo (coluna \"tipo\").");
                    }
                    assetService.create(orgId, userId, new CreateAssetRequest(
                            tag, name, type.getId(),
                            location != null ? location.getId() : null,
                            null, // responsibleUserId: a folha traz o nome, não o utilizador
                            row.getAny(COL_MANUFACTURER), row.getAny(COL_MODEL),
                            row.getAny(COL_SERIAL), integer(row.getAny(COL_YEAR)),
                            row.getAny(COL_PLATE), row.getAny(COL_RESPONSIBLE),
                            // aquisição (data, valor, moeda), foto e objetivo não vêm na folha
                            null, null, null, null, null,
                            row.getAny(COL_NOTES),
                            null, null, null, // estado e coordenadas ficam nos valores por omissão
                            decimal(row.getAny(COL_SPEED)),
                            decimal(row.getAny(COL_METER)),
                            decimal(row.getAny(COL_TANK)),
                            decimal(row.getAny(COL_DOWNTIME))));
                } else if (type == null && row.getAny(COL_TYPE) == null) {
                    throw new IllegalArgumentException("Falta o tipo de ativo (coluna \"tipo\").");
                }
                created++;

            } catch (IllegalArgumentException | ApiException e) {
                errors.add(new RowError(row.lineNumber(), tag, message(e)));
            }
        }

        if (!dryRun) {
            audit.record(orgId, userId, "import.assets", "Organization", orgId,
                    created + " criados, " + updated + " atualizados, " + errors.size() + " erros");
        }
        return new ImportReport("assets", dryRun, sheet.rows().size(), created, updated,
                errors.size(), List.copyOf(createdReferences), errors);
    }

    // ==== Peças =========================================================
    @Transactional
    public ImportReport importParts(String orgId, String userId, String content, boolean dryRun) {
        Csv.Sheet sheet = read(content);
        Map<String, Part> byName = index(
                parts.findByOrganizationIdOrderByNameAsc(orgId), Part::getName);

        List<RowError> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (Csv.Row row : sheet.rows()) {
            String name = row.getAny(COL_NAME);
            try {
                if (name == null) {
                    throw new IllegalArgumentException("Falta o nome da peça.");
                }
                SavePartRequest req = new SavePartRequest(
                        name,
                        row.getAny(COL_PART_NUMBER),
                        row.getAny(COL_SYSTEM),
                        partCategory(row.getAny("categoria")),
                        row.getAny(COL_UNIT),
                        decimal(row.getAny(COL_MIN_QTY)),
                        decimal(row.getAny(COL_COST)),
                        row.getAny(COL_CURRENCY),
                        row.getAny(COL_NOTES));

                Part existing = byName.get(key(name));
                if (existing != null) {
                    if (!dryRun) {
                        stockService.updatePart(orgId, userId, existing.getId(), req);
                    }
                    updated++;
                } else {
                    if (!dryRun) {
                        stockService.createPart(orgId, userId, req);
                    }
                    created++;
                }
            } catch (IllegalArgumentException | ApiException e) {
                errors.add(new RowError(row.lineNumber(), name, message(e)));
            }
        }

        if (!dryRun) {
            audit.record(orgId, userId, "import.parts", "Organization", orgId,
                    created + " criadas, " + updated + " atualizadas, " + errors.size() + " erros");
        }
        return new ImportReport("parts", dryRun, sheet.rows().size(), created, updated,
                errors.size(), List.of(), errors);
    }

    // ==== Abastecimentos ================================================

    /**
     * Importa abastecimentos de uma folha.
     *
     * <p>E o caminho normal de quem recebe do posto ou da gestora de cartoes um
     * ficheiro com centenas de linhas por mes. Lancar isso a mao e o que faz
     * uma empresa desistir de controlar o combustivel.
     *
     * <p>Ao contrario dos ativos e das pecas, um abastecimento nunca se
     * atualiza: cada linha e um acontecimento com data propria. Importar duas
     * vezes o mesmo ficheiro cria duplicados -- que e precisamente o que a
     * regra de abastecimento duplicado depois assinala.
     */
    /*
     * Sem @Transactional de proposito, ao contrario dos outros importadores.
     *
     * O FuelService.record() tem transacao propria. Quando uma linha e recusada
     * -- data no futuro, viatura desconhecida -- essa transacao interna marca a
     * de fora para reversao. Apanhar a excecao no ciclo nao desfaz a marca: o
     * lote seguia ate ao fim e rebentava no commit com "transaction silently
     * rolled back", deitando fora as linhas boas e devolvendo 500 em vez da
     * lista de erros.
     *
     * Sem transacao a envolver tudo, cada linha grava ou falha por si -- que e
     * exatamente o que um relatorio "2 criados, 2 erros" promete.
     */
    public ImportReport importFuel(String orgId, String userId, String content, boolean dryRun) {
        Csv.Sheet sheet = read(content);
        Map<String, Asset> byTag = index(assets.findByOrganizationId(orgId), Asset::getTag);
        Map<String, Asset> byPlate = new LinkedHashMap<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            if (a.getPlate() != null && !a.getPlate().isBlank()) {
                byPlate.put(key(a.getPlate()), a);
            }
        }

        List<RowError> errors = new ArrayList<>();
        int created = 0;

        for (Csv.Row row : sheet.rows()) {
            String refAtivo = row.getAny(COL_FUEL_ASSET);
            try {
                if (refAtivo == null) {
                    throw new IllegalArgumentException(
                            "Falta a viatura (coluna \"ativo\" ou \"matricula\").");
                }
                Asset ativo = byTag.get(key(refAtivo));
                if (ativo == null) {
                    ativo = byPlate.get(key(refAtivo));
                }
                if (ativo == null) {
                    // Nao se cria a viatura: um abastecimento de uma viatura que
                    // nao existe e quase sempre um erro de digitacao, e criar o
                    // ativo escondia-o.
                    throw new IllegalArgumentException(
                            "Nao ha nenhuma viatura com a etiqueta ou matricula \""
                                    + refAtivo + "\".");
                }

                BigDecimal litros = decimal(row.getAny(COL_FUEL_LITERS));
                if (litros == null) {
                    throw new IllegalArgumentException("Falta a quantidade de litros.");
                }

                // Validado aqui, e nao so no servico: o ensaio seco nao chega a
                // gravar, e sem esta verificacao prometia linhas que a gravacao
                // real depois recusava. Um ensaio que nao bate com o resultado
                // e pior do que nao ter ensaio nenhum.
                Instant quando = instante(row.getAny(COL_FUEL_DATE));
                if (quando != null && quando.isAfter(Instant.now())) {
                    throw new IllegalArgumentException("A data do abastecimento esta no futuro.");
                }

                SaveFuelRecordRequest req = new SaveFuelRecordRequest(
                        litros,
                        quando,
                        decimal(row.getAny(COL_FUEL_PRICE)),
                        decimal(row.getAny(COL_FUEL_TOTAL)),
                        row.getAny(COL_CURRENCY),
                        decimal(row.getAny(COL_FUEL_METER)),
                        booleano(row.getAny(COL_FUEL_FULL)),
                        row.getAny(COL_FUEL_STATION),
                        row.getAny(COL_FUEL_DRIVER),
                        null, null,
                        row.getAny(COL_NOTES),
                        null, null, null, null,
                        row.getAny(COL_FUEL_CARD),
                        row.getAny(COL_FUEL_INVOICE),
                        tipoCombustivel(row.getAny(COL_FUEL_TYPE)));

                if (!dryRun) {
                    fuelService.record(orgId, userId, ativo.getId(), req);
                }
                created++;

            } catch (IllegalArgumentException | ApiException e) {
                errors.add(new RowError(row.lineNumber(), refAtivo, message(e)));
            }
        }

        if (!dryRun) {
            audit.record(orgId, userId, "import.fuel", "Organization", orgId,
                    created + " abastecimentos, " + errors.size() + " erros");
        }
        return new ImportReport("fuel", dryRun, sheet.rows().size(), created, 0,
                errors.size(), List.of(), errors);
    }

    /** Le "sim"/"nao"/"true"/"1" como o utilizador os escreve numa folha. */
    private static Boolean booleano(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim().toLowerCase();
        return t.startsWith("s") || t.startsWith("y") || t.equals("1") || t.equals("true")
                || t.startsWith("x");
    }

    /**
     * Le a data como ela aparece numa folha angolana.
     *
     * <p>Aceita dia/mes/ano e o formato ISO. Sem hora, assume meio-dia em
     * Luanda: a meia-noite arriscaria cair no dia anterior ao converter para
     * UTC, e um abastecimento no dia errado estraga o calculo do consumo.
     */
    private static Instant instante(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignored) {
            // Nao era ISO completo; tenta os formatos de folha abaixo.
        }
        java.time.ZoneId luanda = java.time.ZoneId.of("Africa/Luanda");
        String[] padroes = {
            "dd/MM/yyyy HH:mm", "dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd",
        };
        for (String p : padroes) {
            try {
                java.time.format.DateTimeFormatter f =
                        java.time.format.DateTimeFormatter.ofPattern(p);
                if (p.contains("HH")) {
                    return java.time.LocalDateTime.parse(t, f).atZone(luanda).toInstant();
                }
                return java.time.LocalDate.parse(t, f).atTime(12, 0).atZone(luanda).toInstant();
            } catch (Exception ignored) {
                // Formato seguinte.
            }
        }
        throw new IllegalArgumentException(
                "Data \"" + v + "\" nao reconhecida. Use dd/mm/aaaa.");
    }

    private static FuelType tipoCombustivel(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim().toUpperCase().replace(" ", "_")
                .replace("Ó", "O").replace("Á", "A").replace("É", "E");
        return switch (t) {
            case "GASOLEO", "DIESEL", "GASOIL" -> FuelType.DIESEL;
            case "GASOLINA", "PETROL", "GASOLINE" -> FuelType.PETROL;
            case "GAS", "GPL", "LPG" -> FuelType.LPG;
            case "ELETRICO", "ELECTRICO", "ELECTRIC" -> FuelType.ELECTRIC;
            default -> null;
        };
    }

    // ==== Modelos de ficheiro ===========================================
    /** Cabecalho de exemplo para abastecimentos. */
    public String fuelTemplate() {
        return """
                ativo;data;litros;preco por litro;contador;deposito cheio;posto;\
                motorista;cartao;fatura;combustivel;observacoes
                CAM-101;15/09/2026;150;320;182400;sim;Sonangol Viana;\
                Manuel Kiala;CARD-4412;FT 2026/9912;gasoleo;
                """;
    }

    /** Cabeçalho de exemplo, para quem importa não ter de adivinhar as colunas. */
    public String assetTemplate() {
        return """
                tag;nome;tipo;local;fabricante;modelo;numero de serie;ano;matricula;\
                responsavel;horimetro;limite de velocidade;capacidade do deposito;observacoes
                RE-001;Retroescavadora;Retroescavadora;Obra Luanda Sul;Volvo;BL71B;\
                VCE0BL71C00012345;2023;;João Silva;1250;40;160;Comprada nova
                GER-001;Gerador 150 kVA;Gerador;Parque de Máquinas;Cummins;C150D5;\
                CAT00C15XXXXXXXX;2021;;;3400;;300;
                """;
    }

    public String partTemplate() {
        return """
                nome;numero de peca;sistema;categoria;unidade;stock minimo;custo medio;moeda
                Filtro de óleo do motor;1R-0716;ENGINE;FILTER;un;10;8500;AOA
                Óleo hidráulico 20L;HYD-20L;HYDRAULIC;LUBRICANT;un;5;42000;AOA
                """;
    }

    // ==== Auxiliares ====================================================
    private Csv.Sheet read(String content) {
        Csv.Sheet sheet;
        try {
            sheet = Csv.parse(content);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        if (sheet.rows().size() > MAX_ROWS) {
            throw ApiException.badRequest(
                    "O ficheiro tem " + sheet.rows().size() + " linhas; o máximo é " + MAX_ROWS
                            + ". Divida-o em partes.");
        }
        return sheet;
    }

    /**
     * Tipo de ativo indicado na linha. Se não existir é criado — sem isto, uma
     * folha com um tipo novo falharia em todas as linhas. Os nomes criados vão
     * no relatório para quem importa poder confirmar que não foram gralhas.
     */
    private AssetType resolveType(
            String orgId, String userId, Csv.Row row, Map<String, AssetType> byName,
            Set<String> createdReferences, boolean dryRun) {

        String name = row.getAny(COL_TYPE);
        if (name == null) {
            return null;
        }
        AssetType existing = byName.get(key(name));
        if (existing != null) {
            return existing;
        }
        createdReferences.add("Tipo de ativo: " + name);
        if (dryRun) {
            return null;
        }
        String id = assetTypeService.create(orgId, userId, new CreateAssetTypeRequest(
                name, guessCategory(name), null, null, null, true, null)).id();
        AssetType created = assetTypes.findById(id).orElseThrow();
        byName.put(key(name), created);
        return created;
    }

    private Location resolveLocation(
            String orgId, String userId, Csv.Row row, Map<String, Location> byName,
            Set<String> createdReferences, boolean dryRun) {

        String name = row.getAny(COL_LOCATION);
        if (name == null) {
            return null;
        }
        Location existing = byName.get(key(name));
        if (existing != null) {
            return existing;
        }
        createdReferences.add("Local: " + name);
        if (dryRun) {
            return null;
        }
        String id = locationService.create(orgId, userId, new CreateLocationRequest(
                name, null, LocationKind.SITE, null, null, null, null,
                // Campos de filial: um local criado pela importacao e um SITE.
                null, null, null, null, null, null, null)).id();
        Location created = locations.findById(id).orElseThrow();
        byName.put(key(name), created);
        return created;
    }

    /**
     * Reimportar atualiza o que veio preenchido e deixa o resto como está — uma
     * coluna em branco é ausência de informação, não uma ordem para apagar.
     */
    private void applyToExisting(Asset asset, Csv.Row row, AssetType type, Location location) {
        String name = row.getAny(COL_NAME);
        if (name != null) asset.setName(name);
        if (type != null) asset.setAssetType(type);
        if (location != null) asset.setLocation(location);

        String manufacturer = row.getAny(COL_MANUFACTURER);
        if (manufacturer != null) asset.setManufacturer(manufacturer);
        String model = row.getAny(COL_MODEL);
        if (model != null) asset.setModel(model);
        String serial = row.getAny(COL_SERIAL);
        if (serial != null) asset.setSerialNumber(serial);
        Integer year = integer(row.getAny(COL_YEAR));
        if (year != null) asset.setModelYear(year);
        String plate = row.getAny(COL_PLATE);
        if (plate != null) asset.setPlate(plate);
        String responsible = row.getAny(COL_RESPONSIBLE);
        if (responsible != null) asset.setResponsibleLabel(responsible);
        BigDecimal speed = decimal(row.getAny(COL_SPEED));
        if (speed != null) asset.setSpeedLimitKph(speed.signum() > 0 ? speed : null);
        String notes = row.getAny(COL_NOTES);
        if (notes != null) asset.setNotes(notes);
    }

    /** Palpite razoável a partir do nome, para o tipo criado não nascer errado. */
    private AssetCategory guessCategory(String name) {
        String lower = Csv.normalizeHeader(name);
        if (lower.contains("gerador") || lower.contains("generator")) {
            return AssetCategory.GENERATOR;
        }
        if (lower.contains("camiao") || lower.contains("carrinha") || lower.contains("viatura")
                || lower.contains("ligeiro") || lower.contains("pesado")) {
            return AssetCategory.VEHICLE;
        }
        return AssetCategory.MACHINE;
    }

    private PartCategory partCategory(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PartCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria de peça desconhecida: " + value);
        }
    }

    private static <T> Map<String, T> index(List<T> items, java.util.function.Function<T, String> name) {
        Map<String, T> map = new HashMap<>();
        for (T item : items) {
            String value = name.apply(item);
            if (value != null) {
                map.putIfAbsent(key(value), item);
            }
        }
        return map;
    }

    /** Chave de comparação: sem acentos, sem espaços, sem maiúsculas. */
    private static String key(String value) {
        return Csv.normalizeHeader(value);
    }

    private static Integer integer(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: " + value);
        }
    }

    /** Aceita "1 250,50" e "1250.50" — as folhas vêm nas duas convenções. */
    private static BigDecimal decimal(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace(" ", "").replace(" ", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            // "1.250,50": o ponto é separador de milhares.
            cleaned = cleaned.replace(".", "").replace(',', '.');
        } else {
            cleaned = cleaned.replace(',', '.');
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: " + value);
        }
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : "Linha inválida.";
    }
}
