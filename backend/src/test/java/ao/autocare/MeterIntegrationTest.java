package ao.autocare;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class MeterIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private String newAssetWithHourmeter(String tag, double initial) throws Exception {
        MvcResult t = mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Máquina " + tag))))
                .andExpect(status().isCreated()).andReturn();
        String typeId = json.readTree(t.getResponse().getContentAsString()).get("id").asText();

        MvcResult a = mvc.perform(post("/api/v1/assets")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId,
                                "initialMeterValue", initial))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(a.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode addReading(String assetId, Object value, Instant at) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("value", value);
        if (at != null) body.put("readingAt", at.toString());
        MvcResult res = mvc.perform(post("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void recordsReadingsUpdatesCurrentValueAndDailyAverage() throws Exception {
        bearer = register("meter1@teste.ao").bearer();
        String assetId = newAssetWithHourmeter("M-1", 1000);

        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        addReading(assetId, 1000, tenDaysAgo);
        JsonNode r2 = addReading(assetId, 1080, Instant.now());

        assert !r2.get("reading").get("flagged").asBoolean();
        org.assertj.core.api.Assertions.assertThat(r2.get("meter").get("currentValue").asDouble())
                .isEqualTo(1080.0);
        // 80 h em 10 dias -> ~8 h/dia
        org.assertj.core.api.Assertions.assertThat(r2.get("meter").get("dailyAverage").asDouble())
                .isBetween(7.5, 8.5);
    }

    @Test
    void flagsReadingLowerThanPrevious() throws Exception {
        bearer = register("meter2@teste.ao").bearer();
        String assetId = newAssetWithHourmeter("M-2", 0);

        addReading(assetId, 600, Instant.now().minus(2, ChronoUnit.DAYS));
        JsonNode back = addReading(assetId, 550, Instant.now());

        org.assertj.core.api.Assertions.assertThat(back.get("reading").get("flagged").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(back.get("reading").get("flagReason").asText())
                .contains("inferior");
        // valor corrente NÃO recua
        org.assertj.core.api.Assertions.assertThat(back.get("meter").get("currentValue").asDouble())
                .isEqualTo(600.0);
    }

    @Test
    void flagsHourIncreaseGreaterThanElapsedTime() throws Exception {
        bearer = register("meter3@teste.ao").bearer();
        String assetId = newAssetWithHourmeter("M-3", 0);

        addReading(assetId, 10, Instant.now().minus(2, ChronoUnit.HOURS));
        // +200 h em 2 horas reais -> impossível
        JsonNode jump = addReading(assetId, 210, Instant.now());

        org.assertj.core.api.Assertions.assertThat(jump.get("reading").get("flagged").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(jump.get("reading").get("flagReason").asText())
                .contains("tempo decorrido");
    }

    @Test
    void listsHistoryNewestFirst() throws Exception {
        bearer = register("meter4@teste.ao").bearer();
        String assetId = newAssetWithHourmeter("M-4", 0);
        addReading(assetId, 5, Instant.now().minus(3, ChronoUnit.DAYS));
        addReading(assetId, 12, Instant.now().minus(1, ChronoUnit.DAYS));

        mvc.perform(get("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].value").value(12))
                .andExpect(jsonPath("$.content[1].value").value(5));
    }

    @Test
    void rejectsUnknownMeterKind() throws Exception {
        bearer = register("meter5@teste.ao").bearer();
        String assetId = newAssetWithHourmeter("M-5", 0);

        mvc.perform(post("/api/v1/assets/" + assetId + "/meters/ODOMETER/readings")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("value", 100))))
                .andExpect(status().isNotFound());
    }
}
