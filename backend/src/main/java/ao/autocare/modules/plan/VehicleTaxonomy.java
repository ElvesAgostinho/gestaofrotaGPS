package ao.autocare.modules.plan;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Reconhecer o que é cada viatura — pelo tipo, pela marca, pelo modelo e pelo ano.
 *
 * <p>Um sistema de manutenção que trata um Corolla, um Hilux, uma Hiace e um
 * Actros como «viatura» está a mentir a quem o usa: não têm os mesmos sistemas,
 * não se avariam pelas mesmas razões e não se mantêm com o mesmo plano. Um
 * pick-up tem caixa de transferência e diferencial dianteiro; um sedan não tem
 * nem uma coisa nem outra. Uma carrinha de nove lugares leva pessoas e trava
 * com peso em cima. Isto decide-se a olhar para o que a empresa escreveu.
 *
 * <p>A ordem em que se procura é deliberada: <b>modelo antes de marca</b>,
 * porque a Toyota faz o Corolla e o Land Cruiser, e <b>marca antes de tipo</b>,
 * porque «viatura de serviço» não diz nada e «Hilux» diz tudo. Quando nada é
 * reconhecido, não se inventa: devolve-se a família genérica, e o gestor
 * corrige à mão — um palpite errado num plano de manutenção é pior do que
 * nenhum palpite.
 *
 * <p>Os modelos escolhidos são os que andam nas estradas de Angola. A lista
 * cresce com o tempo; o que não pode acontecer é o sistema fingir que sabe.
 */
public final class VehicleTaxonomy {

    private VehicleTaxonomy() {}

    /** Um ativo, como a empresa o descreveu. */
    public record Identificacao(
            String categoria,
            String tipoNome,
            String marca,
            String modelo,
            Integer ano) {}

    /** O que se reconheceu, e com que confiança. */
    public record Resultado(
            String familia,
            /** MODELO, MARCA, TIPO ou CATEGORIA: onde é que a decisão foi tomada. */
            String origem,
            String explicacao) {}

    // ==== Modelos, por família ==============================================
    // Cada entrada é um pedaço de texto que aparece no modelo escrito pelo
    // cliente. Escritos sem acentos e em minúsculas, como a comparação os vê.

    private static final List<String> PICKUP = List.of(
            "hilux", "ranger", "d-max", "dmax", "navara", "np300", "amarok", "l200", "triton",
            "bt-50", "bt50", "frontier", "tundra", "f-150", "f150", "gladiator", "musso",
            "grand tiger", "terra", "rodeo", "pick up", "pick-up", "pickup", "caminhonete");

    private static final List<String> SUV = List.of(
            "land cruiser", "landcruiser", "prado", "fortuner", "pajero", "montero", "patrol",
            "x-trail", "xtrail", "rav4", "cr-v", "crv", "tucson", "santa fe", "sorento", "sportage",
            "discovery", "defender", "range rover", "grand cherokee", "wrangler", "tiguan",
            "touareg", "q5", "q7", "x5", "x3", "gle", "glc", "ml 350", "lx 570", "lx570", "gx 460",
            "everest", "endeavour", "trailblazer", "mu-x", "jipe", "suv", "4x4 fechada");

    private static final List<String> VAN = List.of(
            "hiace", "sprinter", "transit", "ducato", "boxer", "jumper", "master", "movano",
            "h100", "h-100", "starex", "vito", "viano", "caddy", "doblo", "partner", "berlingo",
            "kangoo", "combo", "expert", "jumpy", "scudo", "traffic", "trafic", "van",
            "carrinha de caixa fechada", "furgao", "furgaao");

    private static final List<String> SEDAN = List.of(
            "corolla", "camry", "civic", "accord", "jetta", "passat", "golf", "polo", "yaris",
            "sentra", "altima", "mazda 3", "mazda3", "mazda 6", "focus", "fiesta", "cruze",
            "optima", "elantra", "accent", "rio", "picanto", "i10", "i20", "i30", "c-class",
            "classe c", "e-class", "classe e", "s-class", "classe s", "a3", "a4", "a6",
            "serie 3", "serie 5", "320i", "320d", "520d", "530", "clio", "megane", "symbol",
            "logan", "sandero", "saveiro", "gol", "onix", "versa", "march", "sedan", "berlina");

    private static final List<String> AUTOCARRO = List.of(
            "yutong", "higer", "golden dragon", "king long", "marcopolo", "comil", "busscar",
            "irizar", "setra", "travego", "tourismo", "o500", "o 500", "b9r", "b12", "coaster",
            "rosa", "county", "autocarro", "onibus", "minibus", "midibus", "paradiso", "torino");

    private static final List<String> CAMIAO = List.of(
            "actros", "axor", "atego", "arocs", "fh16", "fh 16", "fh", "fm", "fmx", "fl ", "tgx",
            "tgs", "tga", "trakker", "stralis", "eurocargo", "cargo", "kamaz", "howo", "shacman",
            "sinotruk", "hino 500", "hino 700", "ftr", "fvr", "npr", "canter", "dyna", "kerax",
            "premium", "magnum", "xf", "cf ", "lf ", "scania r", "scania p", "scania g",
            "camiao", "basculante", "betoneira", "autotanque", "cisterna sobre chassis");

    private static final List<String> MAQUINA = List.of(
            "caterpillar", "cat ", "jcb", "komatsu", "hitachi", "doosan", "case ", "new holland",
            "liugong", "sany", "xcmg", "bobcat", "bl71", "bl60", "3cx", "4cx", "320d", "320 d",
            "966", "d6", "d7", "retroescavadora", "escavadora", "giratoria", "pa carregadora",
            "motoniveladora", "niveladora", "cilindro compactador", "rolo compactador",
            "dumper", "bulldozer", "trator de esteira");

    private static final List<String> EMPILHADORA = List.of(
            "empilhadora", "forklift", "linde", "still", "hyster", "yale", "8fg", "clark",
            "toyota 8f", "porta-paletes", "porta paletes");

    private static final List<String> GERADOR = List.of(
            "gerador", "generator", "grupo electrogeneo", "grupo eletrogeneo", "genset",
            "cummins c", "perkins", "fg wilson", "sdmo", "himoinsa", "kohler");

    private static final List<String> REBOQUE = List.of(
            "reboque", "semirreboque", "semi-reboque", "atrelado", "alfaia", "implemento",
            "cisterna", "porta-contentor", "prancha", "grade de discos", "charrua", "pulverizador");

    // ==== A decisão =========================================================

    /**
     * A família de um ativo, e porquê.
     *
     * <p>Devolve sempre alguma coisa. O que muda é a confiança: reconhecido
     * pelo modelo é quase certo; pela categoria é um palpite razoável.
     */
    public static Resultado classificar(Identificacao id) {
        String modelo = limpar(id.modelo());
        String marca = limpar(id.marca());
        String tipo = limpar(id.tipoNome());
        String tudo = (marca + " " + modelo).trim();

        // 1. O modelo é o que decide — é o que distingue um Corolla de um Land Cruiser.
        Resultado porModelo = porTexto(tudo, "MODELO", id.marca(), id.modelo());
        if (porModelo != null) {
            return porModelo;
        }

        // 2. Depois o nome do tipo de equipamento, escrito pela empresa.
        Resultado porTipo = porTexto(tipo, "TIPO", null, id.tipoNome());
        if (porTipo != null) {
            return porTipo;
        }

        // 3. Por fim a categoria, que é grosseira mas nunca está errada.
        String categoria = id.categoria();
        if ("GENERATOR".equals(categoria)) {
            return new Resultado("GENERATOR", "CATEGORIA", "Categoria «geradores».");
        }
        if ("IMPLEMENT".equals(categoria)) {
            return new Resultado("IMPLEMENT", "CATEGORIA", "Categoria «alfaias e implementos».");
        }
        if ("VEHICLE".equals(categoria)) {
            return new Resultado("LIGHT_VEHICLE", "CATEGORIA",
                    "Categoria «viaturas», sem marca nem modelo reconhecidos — "
                            + "escreva o modelo para o plano ser o certo.");
        }
        return new Resultado("RETROESCAVADORA", "CATEGORIA",
                "Categoria «máquinas», sem modelo reconhecido.");
    }

    private static Resultado porTexto(String texto, String origem, String marca, String modelo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String rotulo = (marca != null && !marca.isBlank() ? marca + " " : "")
                + (modelo != null ? modelo : "");
        // A ordem importa: o que é mais específico primeiro. Um «Sprinter» é
        // uma carrinha antes de ser um veículo de mercadorias; um «Coaster» é
        // autocarro antes de ser Toyota.
        if (contem(texto, GERADOR)) {
            return new Resultado("GENERATOR", origem, "Reconhecido como grupo electrogéneo: " + rotulo);
        }
        if (contem(texto, EMPILHADORA)) {
            return new Resultado("FORKLIFT", origem, "Reconhecido como empilhadora: " + rotulo);
        }
        if (contem(texto, REBOQUE)) {
            return new Resultado("IMPLEMENT", origem, "Reconhecido como equipamento rebocado: " + rotulo);
        }
        if (contem(texto, AUTOCARRO)) {
            return new Resultado("BUS", origem, "Reconhecido como autocarro: " + rotulo);
        }
        if (contem(texto, MAQUINA)) {
            return new Resultado("RETROESCAVADORA", origem, "Reconhecido como máquina de obra: " + rotulo);
        }
        if (contem(texto, PICKUP)) {
            return new Resultado("PICKUP", origem, "Reconhecido como pick-up: " + rotulo);
        }
        if (contem(texto, SUV)) {
            return new Resultado("SUV", origem, "Reconhecido como jipe / SUV: " + rotulo);
        }
        if (contem(texto, VAN)) {
            return new Resultado("VAN", origem, "Reconhecido como carrinha: " + rotulo);
        }
        if (contem(texto, SEDAN)) {
            return new Resultado("SEDAN", origem, "Reconhecido como ligeiro de passageiros: " + rotulo);
        }
        if (contem(texto, CAMIAO)) {
            return new Resultado("TRUCK_HEAVY", origem, "Reconhecido como camião pesado: " + rotulo);
        }
        // «Transporte de passageiros» é um autocarro; «ligeiro de passageiros»
        // é um carro. A palavra sozinha não chega — o que está ao lado é que
        // decide, e é assim que um humano também o leria.
        if (texto.contains("passageiro") && !texto.contains("ligeir")
                && !texto.contains("carrinha") && !texto.contains("van")) {
            return new Resultado("BUS", origem,
                    "Transporte de passageiros, tratado como autocarro: " + rotulo);
        }
        return null;
    }

    /**
     * O que a idade da viatura muda no plano.
     *
     * <p>Um camião de 2005 não se mantém com os intervalos de um de 2023, e
     * toda a gente sabe disso menos os sistemas. Acima dos dez anos aperta-se
     * o que envelhece por si: mangueiras, correias, travões e arrefecimento —
     * que é, não por acaso, o que faz arder um autocarro em viagem.
     *
     * <p>Devolve o factor a aplicar aos intervalos desses sistemas (1 = sem
     * alteração). Nunca alarga: uma viatura velha não pode esperar mais.
     */
    public static double factorDeIdade(Integer ano) {
        if (ano == null) {
            return 1;
        }
        int idade = java.time.Year.now().getValue() - ano;
        if (idade >= 20) {
            return 0.6;
        }
        if (idade >= 10) {
            return 0.75;
        }
        return 1;
    }

    /** A frase que explica o aperto, para ficar escrita no plano. */
    public static String notaDeIdade(Integer ano) {
        double f = factorDeIdade(ano);
        if (f >= 1) {
            return null;
        }
        int idade = java.time.Year.now().getValue() - ano;
        return "Viatura com " + idade + " anos: os intervalos de travagem, arrefecimento, "
                + "mangueiras e correias foram encurtados em "
                + Math.round((1 - f) * 100) + " % face ao plano de fábrica. "
                + "O que envelhece com o tempo não espera pelos quilómetros.";
    }

    private static boolean contem(String texto, List<String> chaves) {
        for (String chave : chaves) {
            if (texto.contains(chave)) {
                return true;
            }
        }
        return false;
    }

    private static String limpar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
