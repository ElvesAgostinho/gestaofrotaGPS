package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O sistema calcula o percurso em vez de o pedir escrito.
 *
 * <p>O motor é exercitado contra um <b>servidor HTTP a sério</b>, levantado
 * aqui, e não contra um duplo em memória. O que este código tem de errado com
 * mais probabilidade não é a lógica — é o formato do pedido. Trocar a ordem da
 * latitude e da longitude no URL devolve um caminho no meio do Atlântico, e um
 * duplo que recebesse os pontos já em objetos deixaria passar esse erro
 * exatamente como o deixou passar em produção.
 */
class RouteEngineIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private HttpServer motor;

    /** O que o falso OSRM recebeu, para se conferir o pedido. */
    private final AtomicReference<String> ultimoPedido = new AtomicReference<>();

    /** O que o falso OSRM responde a seguir. */
    private final AtomicReference<String> resposta = new AtomicReference<>();

    private final AtomicReference<Integer> estado = new AtomicReference<>(200);

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("rotas@teste.ao").bearer();

        motor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        motor.createContext("/", troca -> {
            ultimoPedido.set(troca.getRequestURI().toString());
            byte[] corpo = resposta.get().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(estado.get(), corpo.length);
            try (OutputStream out = troca.getResponseBody()) {
                out.write(corpo);
            }
        });
        motor.start();
    }

    @AfterEach
    void tearDown() {
        if (motor != null) {
            motor.stop(0);
        }
    }

    private String enderecoDoMotor() {
        return "http://127.0.0.1:" + motor.getAddress().getPort();
    }

    private void apontarAoMotor() throws Exception {
        send(put("/api/v1/integrations/routing"), Map.of("url", enderecoDoMotor()), 200);
    }

    /** Um local com ponto no mapa. */
    private String local(String nome, String lat, String lon) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", nome);
        body.put("kind", "BRANCH");
        body.put("latitude", lat);
        body.put("longitude", lon);
        return send(post("/api/v1/locations"), body, 201).get("id").asText();
    }

    private static Map<String, Object> pontoLocal(String id) {
        Map<String, Object> p = new HashMap<>();
        p.put("locationId", id);
        return p;
    }

    // ===================================================================

    @Test
    void calculaPelasEstradasQuandoHaMotor() throws Exception {
        apontarAoMotor();
        resposta.set("""
                {"code":"Ok","routes":[{
                  "distance":497300.0,
                  "duration":21600.0,
                  "geometry":{"type":"LineString","coordinates":[[13.23,-8.83],[13.58,-12.35]]}
                }]}""");

        String luanda = local("Filial de Luanda", "-8.8383", "13.2344");
        String lobito = local("Filial do Lobito", "-12.3644", "13.5456");

        JsonNode r = send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(luanda), pontoLocal(lobito))), 200);

        assertThat(r.get("source").asText()).isEqualTo("ENGINE");
        assertThat(r.get("distanceKm").asDouble()).isEqualTo(497.3);
        assertThat(r.get("durationMinutes").asInt()).isEqualTo(360);
        assertThat(r.get("geojson").asText()).contains("LineString");
        // Campo nulo nao viaja no JSON: ausente e a forma de dizer «sem aviso».
        assertThat(r.has("warning")).isFalse();
    }

    @Test
    void mandaLongitudePrimeiroComoOsrmExige() throws Exception {
        apontarAoMotor();
        resposta.set("{\"code\":\"Ok\",\"routes\":[{\"distance\":1000.0,\"duration\":120.0}]}");

        String a = local("A", "-8.8383", "13.2344");
        String b = local("B", "-8.8200", "13.2600");
        send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(a), pontoLocal(b))), 200);

        // Longitude,latitude — trocar isto punha o percurso no Atlântico, e o
        // erro seria silencioso: o motor responderia na mesma.
        assertThat(ultimoPedido.get()).contains("13.2344,-8.8383;13.26,-8.82");
    }

    @Test
    void estimaEmLinhaRetaEAvisaQuandoNaoHaMotor() throws Exception {
        String luanda = local("Luanda", "-8.8383", "13.2344");
        String lobito = local("Lobito", "-12.3644", "13.5456");

        JsonNode r = send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(luanda), pontoLocal(lobito))), 200);

        assertThat(r.get("source").asText()).isEqualTo("STRAIGHT");
        // Linha reta ~394 km, vezes o fator de estrada de 1,35.
        assertThat(r.get("distanceKm").asDouble()).isBetween(500.0, 560.0);
        // O aviso não é decoração: sem ele, uma estimativa passa por medição.
        assertThat(r.get("warning").asText()).contains("linha reta");
        // Sem motor nao ha tracado: desenhar uma linha reta por cima das
        // estradas daria a entender que a viatura vai por ali.
        assertThat(r.has("geojson")).isFalse();
    }

    @Test
    void caiParaLinhaRetaQuandoOMotorFalha() throws Exception {
        apontarAoMotor();
        estado.set(500);
        resposta.set("erro");

        String a = local("A", "-8.8383", "13.2344");
        String b = local("B", "-8.8200", "13.2600");

        JsonNode r = send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(a), pontoLocal(b))), 200);

        // Falhar a criação de uma rota porque o servidor de mapas está em baixo
        // seria travar o trabalho por causa de um acessório.
        assertThat(r.get("source").asText()).isEqualTo("STRAIGHT");
        assertThat(r.get("warning").asText()).contains("não respondeu");
    }

    @Test
    void caiParaLinhaRetaQuandoNaoHaCaminho() throws Exception {
        apontarAoMotor();
        resposta.set("{\"code\":\"NoRoute\",\"routes\":[]}");

        String a = local("Ilha", "-8.7800", "13.2300");
        String b = local("Obra", "-9.1000", "13.5000");

        JsonNode r = send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(a), pontoLocal(b))), 200);

        assertThat(r.get("source").asText()).isEqualTo("STRAIGHT");
    }

    @Test
    void passaPelosPontosDePassagemPelaOrdemDada() throws Exception {
        apontarAoMotor();
        resposta.set("{\"code\":\"Ok\",\"routes\":[{\"distance\":5000.0,\"duration\":600.0}]}");

        String a = local("Origem", "-8.8000", "13.2000");
        String meio = local("Meio", "-8.8500", "13.2500");
        String b = local("Destino", "-8.9000", "13.3000");

        send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(a), pontoLocal(meio), pontoLocal(b))), 200);

        assertThat(ultimoPedido.get())
                .contains("13.2,-8.8;13.25,-8.85;13.3,-8.9");
    }

    @Test
    void preveOCombustivelPeloConsumoDaViatura() throws Exception {
        apontarAoMotor();
        resposta.set("{\"code\":\"Ok\",\"routes\":[{\"distance\":200000.0,\"duration\":7200.0}]}");

        String a = local("A", "-8.8383", "13.2344");
        String b = local("B", "-9.8383", "13.2344");

        Map<String, Object> pedido = new HashMap<>();
        pedido.put("points", List.of(pontoLocal(a), pontoLocal(b)));
        pedido.put("litersPer100Km", "35");

        JsonNode r = send(post("/api/v1/routes/calculate"), pedido, 200);

        // 200 km a 35 L/100 km. O motor de mapas não sabe o que a máquina bebe:
        // isto vem do consumo da frota, não do OSRM.
        assertThat(r.get("fuelLiters").asDouble()).isEqualTo(70.0);
    }

    @Test
    void naoCalculaContraLocalSemPontoNoMapa() throws Exception {
        Map<String, Object> semPonto = new HashMap<>();
        semPonto.put("name", "Armazem por marcar");
        semPonto.put("kind", "WAREHOUSE");
        String sem = send(post("/api/v1/locations"), semPonto, 201).get("id").asText();
        String com = local("Com ponto", "-8.8383", "13.2344");

        JsonNode erro = send(post("/api/v1/routes/calculate"),
                Map.of("points", List.of(pontoLocal(com), pontoLocal(sem))), 400);

        // A mensagem diz onde se resolve. Um «pedido invalido» mandava a pessoa
        // procurar.
        assertThat(erro.toString()).contains("marque-o");
    }

    @Test
    void aRotaGuardaDeOndeVeioADistancia() throws Exception {
        String a = local("A", "-8.8383", "13.2344");
        String b = local("B", "-12.3644", "13.5456");

        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Luanda - Lobito");
        rota.put("originLocationId", a);
        rota.put("destinationLocationId", b);
        rota.put("expectedDistanceKm", "497.3");
        rota.put("distanceSource", "ENGINE");
        rota.put("pathGeojson", "{\"type\":\"LineString\"}");

        String id = send(post("/api/v1/routes"), rota, 201).get("id").asText();
        JsonNode lida = send(get("/api/v1/routes/" + id), null, 200);

        // Uma distância medida e uma escrita à mão não podem ler-se igual: quem
        // olha para o desvio de consumo precisa de saber em qual pode confiar.
        assertThat(lida.get("distanceSource").asText()).isEqualTo("ENGINE");
        assertThat(lida.get("pathGeojson").asText()).contains("LineString");
    }

    @Test
    void recusaOrigemDaDistanciaDesconhecida() throws Exception {
        String a = local("A", "-8.8383", "13.2344");
        String b = local("B", "-8.9000", "13.3000");

        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Inventada");
        rota.put("originLocationId", a);
        rota.put("destinationLocationId", b);
        rota.put("distanceSource", "CONFIADO");

        send(post("/api/v1/routes"), rota, 400);
    }

    @Test
    void semDistanceSourceARotaFicaComoManual() throws Exception {
        String a = local("A", "-8.8383", "13.2344");
        String b = local("B", "-8.9000", "13.3000");

        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Escrita a mao");
        rota.put("originLocationId", a);
        rota.put("destinationLocationId", b);
        rota.put("expectedDistanceKm", "40");

        String id = send(post("/api/v1/routes"), rota, 201).get("id").asText();
        assertThat(send(get("/api/v1/routes/" + id), null, 200)
                .get("distanceSource").asText()).isEqualTo("MANUAL");
    }

    @Test
    void oTesteDoMotorReprovaUmServidorSemMapa() throws Exception {
        apontarAoMotor();
        // Um OSRM de pé mas sem o mapa de Angola responde 200 e diz NoRoute.
        // É exatamente a avaria que um teste de «está vivo?» deixaria passar.
        resposta.set("{\"code\":\"NoRoute\",\"routes\":[]}");

        JsonNode r = send(post("/api/v1/integrations/routing/test"), null, 200);

        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("message").asText()).contains("mapa");
    }

    @Test
    void oTesteDoMotorAprovaUmServidorComMapa() throws Exception {
        apontarAoMotor();
        resposta.set("{\"code\":\"Ok\",\"routes\":[{\"distance\":3100.0,\"duration\":300.0}]}");

        JsonNode r = send(post("/api/v1/integrations/routing/test"), null, 200);
        assertThat(r.get("ok").asBoolean()).isTrue();

        JsonNode definicoes = send(get("/api/v1/integrations"), null, 200);
        assertThat(definicoes.get("routingOk").asBoolean()).isTrue();
        assertThat(definicoes.get("routingConfigured").asBoolean()).isTrue();
    }

    @Test
    void oMotorDeUmaEmpresaNaoServeOutra() throws Exception {
        apontarAoMotor();

        String outra = register("outra@teste.ao").bearer();
        String anterior = bearer;
        bearer = outra;
        JsonNode definicoes = send(get("/api/v1/integrations"), null, 200);
        // Cada empresa aponta ao seu motor. Herdar o do vizinho mandaria as
        // coordenadas dos clientes de uma empresa para o servidor de outra.
        assertThat(definicoes.get("routingConfigured").asBoolean()).isFalse();
        bearer = anterior;
    }

    @Test
    void precisaDeDoisPontos() throws Exception {
        String a = local("A", "-8.8383", "13.2344");
        List<Map<String, Object>> um = new ArrayList<>();
        um.add(pontoLocal(a));
        send(post("/api/v1/routes/calculate"), Map.of("points", um), 400);
    }
}
