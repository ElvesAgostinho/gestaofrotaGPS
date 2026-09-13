package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * O dia de uma viatura para ver de novo: percurso, paragens de 3 minutos ou
 * mais (onde e quanto tempo) e ralenti, o motor ligado sem andar.
 */
class DayHistoryIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private void posicao(String key, double lat, double lon, double kph, boolean ignicao, Instant at) throws Exception {
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", lat);
        position.put("longitude", lon);
        position.put("speedKph", kph);
        position.put("ignition", ignicao);
        position.put("satellites", 9);
        position.put("recordedAt", at.toString());
        mvc.perform(post("/api/v1/telemetry/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("deviceId", "IMEI-DIA", "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    @Test
    void oDiaMostraOPercursoAsParagensEORalenti() throws Exception {
        bearer = register("dia@teste.ao").bearer();
        String tipo = json.readTree(mvc.perform(post("/api/v1/asset-types").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Camião\"}")).andReturn().getResponse().getContentAsByteArray()).get("id").asText();
        String assetId = json.readTree(mvc.perform(post("/api/v1/assets").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("tag", "DIA-1", "name", "Camião", "assetTypeId", tipo))))
                .andReturn().getResponse().getContentAsByteArray()).get("id").asText();
        String key = json.readTree(mvc.perform(post("/api/v1/gps-devices").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("externalId", "IMEI-DIA", "assetId", assetId))))
                .andReturn().getResponse().getContentAsByteArray()).get("ingestKey").asText();

        // Um dia inteiro, já passado, para não apanhar a fronteira da meia-noite.
        LocalDate dia = LocalDate.now(ZoneId.of("Africa/Luanda")).minusDays(1);
        Instant t0 = dia.atTime(8, 0).atZone(ZoneId.of("Africa/Luanda")).toInstant();
        // 08:00–08:10 a andar (5 pontos, ~1,1 km por ponto ao longo de Luanda)
        for (int i = 0; i < 5; i++) {
            posicao(key, -8.8300 - i * 0.01, 13.2300, 40, true, t0.plus(i * 2L, ChronoUnit.MINUTES));
        }
        // 08:10–08:20 parado com o motor ligado (ralenti de 10 min)
        for (int i = 0; i <= 5; i++) {
            posicao(key, -8.8700, 13.2300, 0, true, t0.plus(10 + i * 2L, ChronoUnit.MINUTES));
        }
        // 08:20–08:24 a andar
        for (int i = 0; i < 3; i++) {
            posicao(key, -8.8700, 13.2300 + (i + 1) * 0.01, 35, true, t0.plus(20 + i * 2L, ChronoUnit.MINUTES));
        }
        // 08:26–08:28: parado só 2 minutos (não conta como paragem), motor desligado
        posicao(key, -8.8700, 13.2600, 0, false, t0.plus(26, ChronoUnit.MINUTES));
        posicao(key, -8.8700, 13.2600, 0, false, t0.plus(28, ChronoUnit.MINUTES));
        // 08:30 a andar de novo
        posicao(key, -8.8600, 13.2600, 30, true, t0.plus(30, ChronoUnit.MINUTES));

        JsonNode d = json.readTree(mvc.perform(get("/api/v1/assets/" + assetId + "/day?date=" + dia).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(d.get("points").asInt()).isBetween(15, 17); // a entrada descarta um duplicado exato
        assertThat(d.get("ignitionKnown").asBoolean()).isTrue();
        assertThat(d.get("stops")).hasSize(1);
        JsonNode paragem = d.get("stops").get(0);
        // Entre a primeira e a última amostra parada (a entrada pode ter descartado um duplicado exato).
        assertThat(paragem.get("minutes").asInt()).isBetween(6, 10);
        assertThat(paragem.get("idlingMinutes").asInt()).isEqualTo(paragem.get("minutes").asInt());
        assertThat(d.get("idlingMinutes").asInt()).isBetween(8, 12);
        assertThat(d.get("distanceKm").asDouble()).isBetween(7.0, 10.0);
        assertThat(d.get("maxSpeedKph").asDouble()).isEqualTo(40.0);
        assertThat(d.get("track").size()).isEqualTo(d.get("points").asInt());

        // Um dia sem posições: tudo a zero e sem paragens, sem inventar.
        JsonNode vazio = json.readTree(mvc.perform(get("/api/v1/assets/" + assetId + "/day?date=" + dia.minusDays(5)).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(vazio.get("points").asInt()).isZero();
        assertThat(vazio.get("ignitionKnown").asBoolean()).isFalse();
        assertThat(vazio.has("idlingMinutes")).isFalse();

        mvc.perform(get("/api/v1/assets/" + assetId + "/day?date=ontem").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }
}
