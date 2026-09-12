package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Escrever «Lobito» e obter o ponto.
 *
 * <p>O geocodificador é um servidor HTTP levantado aqui, a responder o JSON do
 * Nominatim. Conta-se quantas vezes é chamado: a cache existe para o serviço
 * público não ser martelado, e um teste que não a verificasse deixava passar
 * uma cache que não funciona.
 */
@TestPropertySource(properties = "autocare.geocoder.url=http://127.0.0.1:47391")
class GeocodingIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ao.autocare.modules.geo.GeocodingService geocoding;

    private String bearer;
    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final AtomicReference<String> resposta = new AtomicReference<>("[]");

    private JsonNode send(String caminho) throws Exception {
        MvcResult r = mvc.perform(get(caminho).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private JsonNode procurar(String termo) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/geo/search").param("q", termo).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("geo@teste.ao").bearer();
        // O serviço é um só para toda a suite: a cache de um teste não pode
        // responder a outro.
        geocoding.limparCache();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 47391), 0);
        servidor.createContext("/search", troca -> {
            chamadas.incrementAndGet();
            assertThat(troca.getRequestHeaders().getFirst("User-Agent")).contains("IMBONDEIRO-OS");
            assertThat(troca.getRequestURI().getQuery()).contains("countrycodes=ao");
            byte[] b = resposta.get().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, b.length);
            try (OutputStream out = troca.getResponseBody()) {
                out.write(b);
            }
        });
        servidor.start();

        Map<String, Object> filial = new HashMap<>();
        filial.put("name", "Obra do Lobito");
        filial.put("kind", "SITE");
        filial.put("city", "Lobito");
        filial.put("latitude", "-12.3600");
        filial.put("longitude", "13.5400");
        mvc.perform(post("/api/v1/locations").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(filial)))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    @Test
    void osLocaisDaEmpresaVemPrimeiroEOMapaDepois() throws Exception {
        resposta.set("[{\"name\":\"Lobito\",\"display_name\":\"Lobito, Benguela, Angola\","
                + "\"lat\":\"-12.3506867\",\"lon\":\"13.5464318\"}]");

        JsonNode r = send("/api/v1/geo/search?q=lobito");
        assertThat(r.get("externalAvailable").asBoolean()).isTrue();
        JsonNode lista = r.get("results");
        assertThat(lista).hasSize(2);
        // A empresa primeiro, com o nome que a empresa usa.
        assertThat(lista.get(0).get("source").asText()).isEqualTo("EMPRESA");
        assertThat(lista.get(0).get("name").asText()).isEqualTo("Obra do Lobito");
        assertThat(lista.get(0).get("locationId").asText()).isNotBlank();
        assertThat(lista.get(1).get("source").asText()).isEqualTo("MAPA");
        assertThat(lista.get(1).get("latitude").asDouble()).isEqualTo(-12.3506867);
    }

    @Test
    void aProcuraIgnoraAcentosEMaiusculas() throws Exception {
        resposta.set("[]");
        JsonNode r = procurar("OBRA DO LÓBITO");
        assertThat(r.get("results").get(0).get("name").asText()).isEqualTo("Obra do Lobito");
    }

    @Test
    void aMesmaProcuraNaoVoltaAoServicoPublico() throws Exception {
        resposta.set("[]");
        procurar("Benguela");
        procurar("benguela");
        procurar(" Benguela ");
        // Uma chamada externa para três procuras iguais: a cache faz o resto.
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test
    void semInternetContinuamOsLocaisDaEmpresa() throws Exception {
        servidor.stop(0);
        JsonNode r = send("/api/v1/geo/search?q=lobito");
        assertThat(r.get("externalAvailable").asBoolean()).isFalse();
        assertThat(r.get("results")).hasSize(1);
        assertThat(r.get("results").get(0).get("source").asText()).isEqualTo("EMPRESA");
    }

    @Test
    void osLocaisDeOutraEmpresaNaoAparecem() throws Exception {
        resposta.set("[]");
        String outra = register("geo-outra@teste.ao").bearer();
        MvcResult r = mvc.perform(get("/api/v1/geo/search?q=lobito").header("Authorization", outra))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(r.getResponse().getContentAsString()).get("results")).isEmpty();
    }

    @Test
    void menosDeDoisCaracteresNaoProcura() throws Exception {
        JsonNode r = send("/api/v1/geo/search?q=L");
        assertThat(r.get("results")).isEmpty();
        assertThat(chamadas.get()).isZero();
    }
}
