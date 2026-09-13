package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O extrato do cartão de combustível contra o que os motoristas registaram:
 * o que bate, o que o cartão pagou sem registo, e o que se registou sem o
 * cartão pagar. É onde a fraude aparece.
 */
class FuelCardIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String bearer;
    private String camiao;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode upload(String csv, boolean dryRun) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "extrato.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        MvcResult r = mvc.perform(multipart("/api/v1/imports/fuel-cards").file(file)
                        .param("dryRun", String.valueOf(dryRun)).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private void registo(Instant quando, double litros, double total, String cartao, double contador) throws Exception {
        Map<String, Object> f = new HashMap<>();
        f.put("filledAt", quando.toString());
        f.put("liters", litros);
        f.put("totalCost", total);
        f.put("cardNumber", cartao);
        f.put("meterValue", contador);
        send(post("/api/v1/assets/" + camiao + "/fuel"), f, 201);
    }

    private static String hora(Instant i) {
        return F.format(ZonedDateTime.ofInstant(i, LUANDA));
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("cartoes@teste.ao", "Transportes Cartão").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201)
                .get("id").asText();
        camiao = send(post("/api/v1/assets"), Map.of("tag", "CM-1", "name", "Camião", "assetTypeId", tipo,
                "plate", "LD-11-22-AA", "initialMeterValue", 10_000, "tankCapacityLiters", 400), 201).get("id").asText();
    }

    @Test
    void cruzaOExtratoComOsRegistos() throws Exception {
        Instant d1 = Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant d2 = d1.plus(3, ChronoUnit.DAYS);
        // Dentro do período do extrato (±36 h): só aí se pode dizer que o cartão não pagou.
        Instant d3 = d2.plus(12, ChronoUnit.HOURS);
        // Registado e pago (bate): 180 L / 72 000
        registo(d1, 180, 72_000, "CARD-4412", 10_500);
        // Registado mas o cartão NÃO pagou: 150 L
        registo(d3, 150, 60_000, "CARD-4412", 11_500);

        String csv = "data;cartao;matricula;litros;valor;posto;referencia\n"
                + hora(d1.plus(40, ChronoUnit.MINUTES)) + ";CARD-4412;LD-11-22-AA;182;72800;Sonangol Viana;TX-1\n"
                // Pago sem ninguém registar: 200 L — para onde foi?
                + hora(d2) + ";CARD-4412;LD-11-22-AA;200;80000;Pumangol;TX-2\n";

        JsonNode ensaio = upload(csv, true);
        assertThat(ensaio.get("report").get("dryRun").asBoolean()).isTrue();
        assertThat(ensaio.get("matched").asInt()).isEqualTo(1);
        assertThat(ensaio.get("unmatched").asInt()).isEqualTo(1);
        assertThat(ensaio.get("recordsWithoutCard").asInt()).isEqualTo(1);
        // No ensaio nada fica: nem transações nem anomalias.
        assertThat(send(get("/api/v1/fuel-cards/transactions"), null, 200)).isEmpty();

        JsonNode real = upload(csv, false);
        assertThat(real.get("report").get("created").asInt()).isEqualTo(2);
        assertThat(real.get("matched").asInt()).isEqualTo(1);
        assertThat(real.get("unmatched").asInt()).isEqualTo(1);

        JsonNode tx = send(get("/api/v1/fuel-cards/transactions"), null, 200);
        assertThat(tx).hasSize(2);
        JsonNode semRegisto = send(get("/api/v1/fuel-cards/transactions?status=UNMATCHED"), null, 200);
        assertThat(semRegisto).hasSize(1);
        assertThat(semRegisto.get(0).get("reference").asText()).isEqualTo("TX-2");

        // As anomalias abriram-se, com o dinheiro em causa.
        JsonNode anomalias = send(get("/api/v1/assets/" + camiao + "/fuel/anomalies"), null, 200);
        String texto = anomalias.toString();
        assertThat(texto).contains("CARD_WITHOUT_RECORD").contains("RECORD_WITHOUT_CARD");
        assertThat(texto).contains("ninguém registou");

        // Importar o mesmo extrato outra vez não duplica nada.
        JsonNode outraVez = upload(csv, false);
        assertThat(outraVez.get("report").get("created").asInt()).isZero();
        assertThat(send(get("/api/v1/fuel-cards/transactions"), null, 200)).hasSize(2);

        // Tratar a transação sem registo.
        send(post("/api/v1/fuel-cards/transactions/" + semRegisto.get(0).get("id").asText() + "/ignore"), null, 200);
        assertThat(send(get("/api/v1/fuel-cards/transactions?status=UNMATCHED"), null, 200)).isEmpty();
    }

    @Test
    void semMatriculaOCartaoIdentificaAViatura() throws Exception {
        Instant d1 = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        registo(d1, 100, 40_000, "CARD-77", 10_200);
        String csv = "data;cartao;litros;valor\n" + hora(d1.plus(1, ChronoUnit.HOURS)) + ";CARD-77;101;40400\n";
        JsonNode r = upload(csv, false);
        assertThat(r.get("matched").asInt()).isEqualTo(1);
        JsonNode tx = send(get("/api/v1/fuel-cards/transactions"), null, 200);
        assertThat(tx.get(0).get("assetTag").asText()).isEqualTo("CM-1");
        assertThat(tx.get(0).get("status").asText()).isEqualTo("MATCHED");
    }

    @Test
    void linhasSemDataOuSemLitrosSaoRecusadas() throws Exception {
        String csv = "data;cartao;litros;valor\n;CARD-1;10;100\n01/01/2026 10:00;CARD-1;;\n";
        JsonNode r = upload(csv, true);
        assertThat(r.get("report").get("errors")).hasSize(2);
        assertThat(r.get("report").get("errors").get(0).get("message").asText()).contains("data");
    }
}
