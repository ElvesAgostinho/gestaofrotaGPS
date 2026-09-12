package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.IntegrationSettings;
import ao.autocare.modules.telemetry.Geo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calcula o caminho entre pontos, para que ninguém tenha de o escrever.
 *
 * <p>A distância e a duração previstas de uma rota eram, até aqui, dois números
 * digitados. Ninguém os conferia. «Luanda → Lobito, 40 km» entrava na base de
 * dados sem uma queixa, e a partir daí todos os desvios de consumo medidos
 * contra essa rota eram lixo — com ar de rigor, que é pior do que não ter nada.
 *
 * <p>Fala com um <b>OSRM</b> instalado no servidor da empresa. A escolha é
 * deliberada: a rota de uma frota diz onde estão os clientes e por onde andam as
 * viaturas, e isso não se manda para fora a cada consulta. Também não se paga
 * por consulta nem se fica refém de uma ligação à Internet que o estaleiro pode
 * não ter.
 *
 * <p>Sem motor configurado, responde em linha reta com um fator de estrada — e
 * diz que foi isso que fez. Uma aproximação anunciada é útil; uma aproximação
 * disfarçada de medição é uma mentira que só se descobre no fecho do mês.
 */
@Component
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(15);

    /**
     * Quanto uma estrada real é mais longa do que a linha reta.
     *
     * <p>1,35 é o valor habitual para rede rodoviária interurbana. Serve para
     * dar uma ordem de grandeza quando não há motor — nunca para substituir o
     * cálculo. Dentro de uma cidade erra por defeito; numa autoestrada, por
     * excesso.
     */
    private static final BigDecimal FATOR_ESTRADA = new BigDecimal("1.35");

    /** Velocidade média assumida na estimativa em linha reta, km/h. */
    private static final int VELOCIDADE_ASSUMIDA_KMH = 55;

    private final ObjectMapper json;

    public RoutingEngine(ObjectMapper json) {
        this.json = json;
    }

    /** Um ponto do percurso. */
    public record Ponto(BigDecimal latitude, BigDecimal longitude) {

        public boolean valido() {
            return Geo.isValid(latitude, longitude);
        }
    }

    /** De onde veio o resultado. Acompanha-o até ao ecrã. */
    public enum Fonte {
        /** Calculado pelo motor, pelas estradas reais. */
        ENGINE,
        /** Linha reta com fator de estrada. É uma aproximação e diz-se. */
        STRAIGHT
    }

    /**
     * O resultado do cálculo.
     *
     * @param geojson traçado para o mapa desenhar; nulo quando não há motor,
     *     porque desenhar uma linha reta por cima de estradas daria a entender
     *     que a viatura vai por ali
     */
    public record Trajeto(
            BigDecimal distanceKm,
            Integer durationMinutes,
            String geojson,
            Fonte fonte,
            String aviso) {}

    /**
     * O caminho que liga estes pontos, pela ordem dada.
     *
     * @param pontos origem, passagens e destino — no mínimo dois
     */
    public Trajeto calcular(IntegrationSettings settings, List<Ponto> pontos) {
        if (pontos == null || pontos.size() < 2) {
            throw ApiException.badRequest(
                    "São precisos pelo menos dois pontos para calcular um percurso.");
        }
        for (Ponto p : pontos) {
            if (p == null || !p.valido()) {
                throw ApiException.badRequest(
                        "Há pontos sem coordenadas. Escolha locais registados ou "
                                + "marque-os no mapa.");
            }
        }

        if (settings != null && settings.hasRouting()) {
            Trajeto pelaEstrada = viaMotor(settings.getRoutingUrl(), pontos);
            if (pelaEstrada != null) {
                return pelaEstrada;
            }
        }
        return emLinhaReta(pontos, settings != null && settings.hasRouting());
    }

    /**
     * Pergunta ao OSRM.
     *
     * <p>Devolve {@code null} quando o motor não responde — o cálculo em linha
     * reta entra no lugar, com o aviso. Falhar a criação de uma rota por o
     * servidor de mapas estar em baixo seria travar o trabalho por causa de um
     * acessório.
     */
    private Trajeto viaMotor(String base, List<Ponto> pontos) {
        StringJoiner coords = new StringJoiner(";");
        for (Ponto p : pontos) {
            // O OSRM recebe longitude primeiro. Trocar a ordem devolve um
            // caminho no meio do oceano, e o erro é silencioso.
            coords.add(coordenada(p.longitude()) + "," + coordenada(p.latitude()));
        }
        String url = base + "/route/v1/driving/" + coords
                + "?overview=full&geometries=geojson&alternatives=false&steps=false";

        try {
            HttpResponse<String> r = HttpClient.newBuilder()
                    .connectTimeout(TEMPO_LIMITE)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .timeout(TEMPO_LIMITE)
                                    .header("Accept", "application/json")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            if (r.statusCode() != 200) {
                log.warn("Motor de rotas respondeu {}", r.statusCode());
                return null;
            }

            JsonNode corpo = json.readTree(r.body());
            if (!"Ok".equals(corpo.path("code").asText())) {
                // NoRoute é a resposta honesta a dois pontos que nenhuma estrada
                // liga — uma ilha, uma obra fora da rede cartografada.
                String codigo = corpo.path("code").asText();
                log.info("Motor de rotas sem caminho: {}", codigo);
                return null;
            }

            JsonNode rota = corpo.path("routes").path(0);
            if (rota.isMissingNode()) {
                return null;
            }

            BigDecimal km = BigDecimal.valueOf(rota.path("distance").asDouble())
                    .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
            int minutos = (int) Math.round(rota.path("duration").asDouble() / 60.0);
            String geometria = rota.path("geometry").isMissingNode()
                    ? null : rota.path("geometry").toString();

            return new Trajeto(km, minutos, geometria, Fonte.ENGINE, null);

        } catch (Exception e) {
            log.warn("Motor de rotas indisponível: {}", e.toString());
            return null;
        }
    }

    /**
     * A coordenada como texto, sem os zeros que a coluna arrasta.
     *
     * <p>Uma latitude guardada com sete casas volta da base de dados como
     * {@code -8.8383000}. O motor aceita-a na mesma, mas o URL cresce e fica
     * ilegivel no log -- e e no log que se vai ver o que foi pedido quando um
     * percurso sai errado.
     */
    private static String coordenada(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** Soma das linhas retas entre pontos consecutivos, vezes o fator. */
    private Trajeto emLinhaReta(List<Ponto> pontos, boolean motorFalhou) {
        double metros = 0;
        for (int i = 1; i < pontos.size(); i++) {
            metros += Geo.distanceMeters(
                    pontos.get(i - 1).latitude().doubleValue(),
                    pontos.get(i - 1).longitude().doubleValue(),
                    pontos.get(i).latitude().doubleValue(),
                    pontos.get(i).longitude().doubleValue());
        }
        BigDecimal km = BigDecimal.valueOf(metros / 1000.0)
                .multiply(FATOR_ESTRADA)
                .setScale(1, RoundingMode.HALF_UP);
        int minutos = (int) Math.round(km.doubleValue() / VELOCIDADE_ASSUMIDA_KMH * 60);

        String aviso = motorFalhou
                ? "O motor de rotas não respondeu. Este valor é uma estimativa em "
                        + "linha reta com fator de estrada — confirme-o antes de o usar "
                        + "para medir desvios."
                : "Estimativa em linha reta com fator de estrada. Para ter a distância "
                        + "pelas estradas reais, configure o motor de rotas nas "
                        + "Configurações.";

        return new Trajeto(km, minutos, null, Fonte.STRAIGHT, aviso);
    }
}
