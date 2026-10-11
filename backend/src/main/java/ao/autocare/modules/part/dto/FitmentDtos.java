package ao.autocare.modules.part.dto;

import ao.autocare.domain.PartApplication;
import ao.autocare.domain.PartCrossRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Compatibilidade de peças: onde se monta e o que a substitui. */
public final class FitmentDtos {

    private FitmentDtos() {}

    // ==== Aplicações ========================================================

    public record SaveApplicationRequest(
            @Size(max = 30) String family,
            @Size(max = 80) String make,
            @Size(max = 120) String model,
            @Size(max = 120) String engine,
            Integer yearFrom,
            Integer yearTo,
            @Size(max = 300) String note) {}

    public record ApplicationView(
            String id,
            String family,
            String make,
            String model,
            String engine,
            Integer yearFrom,
            Integer yearTo,
            String note,
            /** «Camiões pesados · Hino 500 · 2015–2020» — a linha como se lê. */
            String resumo) {

        public static ApplicationView of(PartApplication a) {
            return new ApplicationView(a.getId(), a.getFamily(), a.getMake(), a.getModel(),
                    a.getEngine(), a.getYearFrom(), a.getYearTo(), a.getNote(), resumo(a));
        }

        private static String resumo(PartApplication a) {
            if (a.universal()) {
                return "Serve qualquer equipamento";
            }
            List<String> partes = new java.util.ArrayList<>();
            if (a.getFamily() != null && !a.getFamily().isBlank()) {
                partes.add(a.getFamily());
            }
            if (a.getMake() != null && !a.getMake().isBlank()) {
                partes.add(a.getMake());
            }
            if (a.getModel() != null && !a.getModel().isBlank()) {
                partes.add(a.getModel());
            }
            if (a.getEngine() != null && !a.getEngine().isBlank()) {
                partes.add("motor " + a.getEngine());
            }
            if (a.getYearFrom() != null || a.getYearTo() != null) {
                String de = a.getYearFrom() != null ? String.valueOf(a.getYearFrom()) : "…";
                String ate = a.getYearTo() != null ? String.valueOf(a.getYearTo()) : "…";
                partes.add(de + "–" + ate);
            }
            return String.join(" · ", partes);
        }
    }

    // ==== Referências cruzadas ==============================================

    public record SaveCrossRefRequest(
            @NotBlank(message = "Indique a referência.") @Size(max = 80) String number,
            @Size(max = 80) String brand,
            String kind,
            @Size(max = 300) String note) {}

    public record CrossRefView(
            String id, String number, String brand, String kind, String kindLabel, String note) {

        public static CrossRefView of(PartCrossRef r) {
            return new CrossRefView(r.getId(), r.getNumber(), r.getBrand(),
                    r.getKind().name(), etiqueta(r.getKind()), r.getNote());
        }

        private static String etiqueta(PartCrossRef.Kind k) {
            return switch (k) {
                case OEM -> "Original (OEM)";
                case EQUIVALENT -> "Equivalente";
                case SUPERSEDES -> "Substitui";
                case SUPERSEDED_BY -> "Substituída por";
            };
        }
    }

    // ==== Compatibilidade ===================================================

    /**
     * O veredicto de uma peça para um equipamento.
     *
     * <p>São quatro e não dois de propósito. «Não sei» não é «serve», e também
     * não é «não serve»: um sistema que confunde as duas coisas ou deixa montar
     * a peça errada ou enche o ecrã de alarmes falsos.
     */
    public record Compatibilidade(String veredicto, String motivo) {

        /** A peça tem aplicação que encaixa. */
        public static final String SERVE = "SERVE";
        /** Nenhuma aplicação encaixa: é a peça de outro equipamento. */
        public static final String NAO_SERVE = "NAO_SERVE";
        /** Encaixaria, mas falta um dado ao equipamento (ano, motor). */
        public static final String INCERTO = "INCERTO";
        /** A peça não tem compatibilidade declarada — ninguém a preencheu. */
        public static final String SEM_INFORMACAO = "SEM_INFORMACAO";
    }

    /** Uma peça que serve este equipamento, com o armazém ao lado. */
    public record PecaCompativel(
            String partId,
            String name,
            String partNumber,
            String specification,
            String category,
            String systemCode,
            String unit,
            BigDecimal stock,
            BigDecimal minQuantity,
            boolean abaixoDoMinimo,
            Integer leadTimeDays,
            String veredicto,
            String porque,
            List<CrossRefView> referencias) {}

    /** O resultado de procurar um número — o próprio ou de um equivalente. */
    public record Achado(
            String partId,
            String name,
            String partNumber,
            String specification,
            String category,
            BigDecimal stock,
            /** Como se chegou aqui: «referência do artigo» ou «equivalente Mann». */
            String encontradoPor,
            String numero) {}
}
