package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.predictive.FailureForecastService;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Prever a próxima avaria por sistema: três avarias de travões a intervalos
 * regulares dão uma data prevista, uma confiança dita, e um aviso quando
 * está a menos de 14 dias. Uma avaria isolada não dá previsão nenhuma.
 */
class FailureForecastIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FailureForecastService forecasts;

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private void avaria(String orgId, String assetId, String sistema, long diasAtras, long contador) {
        Instant quando = Instant.now().minus(diasAtras, ChronoUnit.DAYS);
        jdbcTemplate.update("insert into failures (id, organization_id, asset_id, system_code, description, cause, "
                        + "detected_at, meter_value, caused_downtime, created_at) values (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), orgId, assetId, sistema, "Pastilhas gastas", "Desgaste",
                Timestamp.from(quando), contador, true, Timestamp.from(quando));
    }

    @Test
    void tresAvariasDeTravoesAIntervalosRegularesDaoUmaPrevisaoEUmAviso() throws Exception {
        bearer = register("prev@teste.ao", "Transportes Previsão").bearer();
        String orgId = send(get("/api/v1/organization"), null, 200).get("id").asText();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of("tag", "PV-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();
        // Uma só avaria noutro sistema: não chega para prever nada.
        avaria(orgId, assetId, "ELECTRICAL", 10, 100_000);
        // Travões: de 60 em 60 dias, de 12 000 em 12 000 km; a última foi há 55 dias.
        avaria(orgId, assetId, "BRAKES", 175, 60_000);
        avaria(orgId, assetId, "BRAKES", 115, 72_000);
        avaria(orgId, assetId, "BRAKES", 55, 84_000);

        JsonNode lista = send(get("/api/v1/predictive/forecast"), null, 200);
        assertThat(lista).hasSize(1);
        JsonNode p = lista.get(0);
        assertThat(p.get("assetTag").asText()).isEqualTo("PV-1");
        assertThat(p.get("systemLabel").asText()).isEqualTo("Travões");
        assertThat(p.get("failures").asInt()).isEqualTo(3);
        assertThat(p.get("meanIntervalDays").asInt()).isEqualTo(60);
        assertThat(p.get("meanIntervalMeter").asInt()).isEqualTo(12_000);
        assertThat(p.get("predictedMeter").asInt()).isEqualTo(96_000);
        assertThat(p.get("daysLeft").asInt()).isBetween(4, 5); // 60 - 55
        assertThat(p.get("confidence").asText()).isEqualTo("MEDIUM");
        assertThat(p.get("risk").asText()).isEqualTo("HIGH");
        assertThat(p.get("suggestion").asText()).contains("ordem preventiva").contains("Travões");

        // A ficha do ativo vê só as suas.
        assertThat(send(get("/api/v1/assets/" + assetId + "/predictive/forecast"), null, 200)).hasSize(1);

        // A 14 dias, quem gere é avisado — uma vez.
        assertThat(forecasts.notifyImminent()).isEqualTo(1);
        assertThat(forecasts.notifyImminent()).isZero();
        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).anySatisfy(n -> {
            assertThat(n.get("title").asText()).startsWith("Avaria provável em").contains("PV-1");
            assertThat(n.get("body").asText()).contains("Travões").contains("3 avarias");
        });
    }
}
