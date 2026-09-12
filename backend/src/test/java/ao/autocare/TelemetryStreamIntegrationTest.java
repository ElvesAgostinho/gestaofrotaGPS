package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.telemetry.TelemetryStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Mapa em tempo real: bilhetes de ligação e fluxo SSE. */
class TelemetryStreamIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    @Autowired
    private TelemetryStream stream;

    private String bearer;
    private String assetId;
    private String key;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private void setUpFleet(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Máquina " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
        key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-S", "assetId", assetId), 201)
                .get("ingestKey").asText();
    }

    private void publish(double lat, double lon) throws Exception {
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", lat);
        position.put("longitude", lon);
        position.put("satellites", 9);
        position.put("speedKph", 40);
        mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-S", "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    // ---- testes -------------------------------------------------------
    @Test
    void issuesASingleUseTicketAndOpensTheStream() throws Exception {
        setUpFleet("st1@teste.ao", "RE-001");

        JsonNode ticket = send(post("/api/v1/telemetry/stream-ticket"), null, 200);
        String value = ticket.get("ticket").asText();
        assertThat(value).isNotBlank();
        assertThat(ticket.get("url").asText()).contains("/api/v1/telemetry/stream?ticket=");
        assertThat(ticket.get("expiresInSeconds").asInt()).isEqualTo(60);

        // Abre o fluxo sem cabeçalho de autenticação — a credencial é o bilhete.
        mvc.perform(get("/api/v1/telemetry/stream").param("ticket", value))
                .andExpect(status().isOk());

        // O mesmo bilhete não serve outra vez.
        mvc.perform(get("/api/v1/telemetry/stream").param("ticket", value))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesUnknownTicketsAndRequiresASessionToGetOne() throws Exception {
        mvc.perform(get("/api/v1/telemetry/stream").param("ticket", "inventado"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/telemetry/stream-ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOpenMapCountsAsAConnectionAndPositionsAreBroadcast() throws Exception {
        setUpFleet("st2@teste.ao", "RE-002");
        String orgId = send(get("/api/v1/organization"), null, 200).get("id").asText();

        assertThat(stream.connectionCount(orgId)).isZero();

        String value = send(post("/api/v1/telemetry/stream-ticket"), null, 200)
                .get("ticket").asText();
        mvc.perform(get("/api/v1/telemetry/stream").param("ticket", value))
                .andExpect(status().isOk());
        assertThat(stream.connectionCount(orgId)).isEqualTo(1);

        // Publicar uma posição não deve rebentar com o mapa ligado.
        publish(LAT, LON);
        assertThat(send(get("/api/v1/telemetry/live"), null, 200)).hasSize(1);
    }

    @Test
    void eachCompanyOnlySeesItsOwnStream() throws Exception {
        setUpFleet("st3@teste.ao", "RE-003");
        String orgA = send(get("/api/v1/organization"), null, 200).get("id").asText();

        String value = send(post("/api/v1/telemetry/stream-ticket"), null, 200)
                .get("ticket").asText();
        mvc.perform(get("/api/v1/telemetry/stream").param("ticket", value))
                .andExpect(status().isOk());

        // Outra empresa, outra ligação — as contagens não se misturam.
        bearer = register("st3b@teste.ao", "Outra Empresa").bearer();
        String orgB = send(get("/api/v1/organization"), null, 200).get("id").asText();
        assertThat(orgB).isNotEqualTo(orgA);

        assertThat(stream.connectionCount(orgA)).isEqualTo(1);
        assertThat(stream.connectionCount(orgB)).isZero();
    }
}
