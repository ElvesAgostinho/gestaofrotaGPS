package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Notificações: quem é avisado, de quê, e o que impede a repetição. */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    private String bearer;

    private JsonNode send(String token, MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (token != null) {
            req = req.header("Authorization", token);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        return send(bearer, req, body, expect);
    }

    private JsonNode inbox(String token) throws Exception {
        return send(token, get("/api/v1/notifications"), null, 200).get("content");
    }

    private void publish(String key, double speed, Instant at) throws Exception {
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", LAT);
        position.put("longitude", LON);
        position.put("speedKph", speed);
        position.put("satellites", 9);
        position.put("recordedAt", at.toString());
        mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-N", "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    /** Convida alguém e devolve o token de acesso da conta criada. */
    private String inviteAndAccept(String email, String role, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("role", role);
        String token = send(post("/api/v1/team/invitations"), body, 201).get("token").asText();
        JsonNode res = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", name, "password", "palavraForte1"), 200);
        return "Bearer " + res.get("accessToken").asText();
    }

    // ---- caixa de entrada ---------------------------------------------
    @Test
    void speedingAlertsTheManagersOnceAndMarksEmailAsDemoMode() throws Exception {
        bearer = register("nt1@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of(
                "tag", "CAM-001", "name", "Camião", "assetTypeId", typeId,
                "speedLimitKph", 60), 201).get("id").asText();
        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-N", "assetId", assetId), 201)
                .get("ingestKey").asText();

        Instant t0 = Instant.now().minus(20, ChronoUnit.MINUTES);
        publish(key, 100, t0);
        publish(key, 110, t0.plus(2, ChronoUnit.MINUTES)); // mesmo episódio

        JsonNode avisos = inbox(bearer);
        assertThat(avisos).hasSize(1);           // um aviso, não um por posição
        JsonNode aviso = avisos.get(0);
        assertThat(aviso.get("category").asText()).isEqualTo("GPS");
        assertThat(aviso.get("categoryLabel").asText()).isEqualTo("GPS e frota");
        assertThat(aviso.get("title").asText()).contains("Excesso de velocidade");
        assertThat(aviso.get("assetTag").asText()).isEqualTo("CAM-001");
        assertThat(aviso.get("read").asBoolean()).isFalse();
        // Sem servidor de email configurado, nunca se marca como enviado.
        assertThat(aviso.get("emailState").asText()).isEqualTo("DEMO_MODE");
        // Sem WhatsApp nem SMS na plataforma, o aviso grave fica «sem canal», nunca «enviado».
        assertThat(aviso.get("phoneState").asText()).isEqualTo("NO_CHANNEL");

        assertThat(send(get("/api/v1/notifications/unread-count"), null, 200)
                .get("unread").asInt()).isEqualTo(1);
    }

    @Test
    void techniciansAreNotBotheredWithFleetAlertsButGetTheirOwnOrders() throws Exception {
        bearer = register("nt2@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Retro"), 201)
                .get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of(
                "tag", "RE-001", "name", "Retro", "assetTypeId", typeId,
                "speedLimitKph", 40), 201).get("id").asText();
        String tecnico = inviteAndAccept("tec2@teste.ao", "TECHNICIAN", "Ana");
        String tecnicoUserId = send(get("/api/v1/team/members"), null, 200)
                .get(1).get("userId").asText();

        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-N", "assetId", assetId), 201)
                .get("ingestKey").asText();
        publish(key, 90, Instant.now().minus(10, ChronoUnit.MINUTES));

        // O alerta de frota é para quem gere, não para o técnico.
        assertThat(inbox(bearer)).hasSize(1);
        assertThat(inbox(tecnico)).isEmpty();

        // Mas uma ordem atribuída é assunto dele.
        send(post("/api/v1/work-orders"), Map.of(
                "assetId", assetId, "type", "PREVENTIVE", "title", "Revisão 500 h",
                "assignedToUserId", tecnicoUserId), 201);

        JsonNode dele = inbox(tecnico);
        assertThat(dele).hasSize(1);
        assertThat(dele.get(0).get("category").asText()).isEqualTo("WORK_ORDER");
        assertThat(dele.get(0).get("title").asText()).contains("atribuída a si");
    }

    @Test
    void lowStockWarnsOnCrossingAndAgainAfterReplenishing() throws Exception {
        bearer = register("nt3@teste.ao").bearer();
        String partId = send(post("/api/v1/parts"), Map.of(
                "name", "Filtro de óleo", "partNumber", "FO-001", "minQuantity", 5), 201)
                .get("id").asText();
        String whId = send(post("/api/v1/warehouses"),
                Map.of("name", "Armazém Central"), 201).get("id").asText();

        Map<String, Object> mov = new HashMap<>();
        mov.put("partId", partId);
        mov.put("warehouseId", whId);
        mov.put("type", "IN");
        mov.put("quantity", 10);
        send(post("/api/v1/stock/movements"), mov, 201);
        assertThat(inbox(bearer)).isEmpty();   // acima do mínimo, nada a dizer

        // Sai 7: passa de 10 para 3, atravessa o mínimo de 5.
        mov.put("type", "OUT_OTHER");
        mov.put("quantity", 7);
        send(post("/api/v1/stock/movements"), mov, 201);
        assertThat(inbox(bearer)).hasSize(1);
        assertThat(inbox(bearer).get(0).get("category").asText()).isEqualTo("STOCK");

        // Mais uma saída não repete o aviso — já se sabe que está em falta.
        mov.put("quantity", 1);
        send(post("/api/v1/stock/movements"), mov, 201);
        assertThat(inbox(bearer)).hasSize(1);

        // Reposto acima do mínimo: o aviso deixa de fazer sentido e desaparece.
        mov.put("type", "IN");
        mov.put("quantity", 20);
        send(post("/api/v1/stock/movements"), mov, 201);
        assertThat(inbox(bearer)).isEmpty();

        // E um novo esgotamento volta a avisar.
        mov.put("type", "OUT_OTHER");
        mov.put("quantity", 20);
        send(post("/api/v1/stock/movements"), mov, 201);
        assertThat(inbox(bearer)).hasSize(1);
    }

    @Test
    void turningOffACategoryStopsTheNotices() throws Exception {
        bearer = register("nt4@teste.ao").bearer();

        JsonNode prefs = send(get("/api/v1/notifications/preferences"), null, 200);
        assertThat(prefs.size()).isGreaterThanOrEqualTo(10);
        JsonNode gps = null;
        for (JsonNode pref : prefs) {
            if ("GPS".equals(pref.get("category").asText())) gps = pref;
        }
        assertThat(gps).isNotNull();
        assertThat(gps.get("inApp").asBoolean()).isTrue();
        assertThat(gps.get("label").asText()).isEqualTo("GPS e frota");

        send(patch("/api/v1/notifications/preferences/GPS"),
                Map.of("inApp", false), 200);

        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of(
                "tag", "CAM-004", "name", "Camião", "assetTypeId", typeId,
                "speedLimitKph", 50), 201).get("id").asText();
        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-N", "assetId", assetId), 201)
                .get("ingestKey").asText();
        publish(key, 120, Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThat(inbox(bearer)).isEmpty();
    }

    @Test
    void geofenceCrossingsProduceNotices() throws Exception {
        bearer = register("nt5@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Máquina"), 201)
                .get("id").asText();
        String assetId = send(post("/api/v1/assets"),
                Map.of("tag", "ESC-001", "name", "Escavadora", "assetTypeId", typeId), 201)
                .get("id").asText();

        Map<String, Object> obra = new HashMap<>();
        obra.put("name", "Obra Luanda Sul");
        obra.put("kind", "CIRCLE");
        obra.put("centerLatitude", LAT);
        obra.put("centerLongitude", LON);
        obra.put("radiusM", 500);
        send(post("/api/v1/geofences"), obra, 201);

        send(post("/api/v1/assets/" + assetId + "/position"),
                Map.of("latitude", LAT, "longitude", LON), 201);
        assertThat(inbox(bearer)).isEmpty();  // a primeira posição só fixa o estado

        send(post("/api/v1/assets/" + assetId + "/position"),
                Map.of("latitude", -8.8183, "longitude", LON), 201);

        JsonNode avisos = inbox(bearer);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("title").asText()).contains("saiu de Obra Luanda Sul");
    }

    @Test
    void readsOneAndReadsAll() throws Exception {
        bearer = register("nt6@teste.ao").bearer();
        String partId = send(post("/api/v1/parts"), Map.of(
                "name", "Correia", "partNumber", "C-1", "minQuantity", 5), 201).get("id").asText();
        String partId2 = send(post("/api/v1/parts"), Map.of(
                "name", "Filtro de ar", "partNumber", "FA-1", "minQuantity", 5), 201).get("id").asText();
        String whId = send(post("/api/v1/warehouses"),
                Map.of("name", "Armazém"), 201).get("id").asText();

        for (String id : List.of(partId, partId2)) {
            Map<String, Object> mov = new HashMap<>();
            mov.put("partId", id);
            mov.put("warehouseId", whId);
            mov.put("type", "IN");
            mov.put("quantity", 10);
            send(post("/api/v1/stock/movements"), mov, 201);
            mov.put("type", "OUT_OTHER");
            mov.put("quantity", 8);
            send(post("/api/v1/stock/movements"), mov, 201);
        }
        assertThat(inbox(bearer)).hasSize(2);

        String first = inbox(bearer).get(0).get("id").asText();
        assertThat(send(post("/api/v1/notifications/" + first + "/read"), null, 200)
                .get("read").asBoolean()).isTrue();
        assertThat(send(get("/api/v1/notifications/unread-count"), null, 200)
                .get("unread").asInt()).isEqualTo(1);

        assertThat(send(get("/api/v1/notifications?unread=true"), null, 200)
                .get("content")).hasSize(1);

        send(post("/api/v1/notifications/read-all"), null, 200);
        assertThat(send(get("/api/v1/notifications/unread-count"), null, 200)
                .get("unread").asInt()).isZero();
    }

    @Test
    void nobodyReadsSomeoneElsesNotifications() throws Exception {
        bearer = register("nt7@teste.ao").bearer();
        String outro = register("nt7b@teste.ao", "Outra Empresa").bearer();

        String partId = send(post("/api/v1/parts"), Map.of(
                "name", "Vela", "partNumber", "V-1", "minQuantity", 5), 201).get("id").asText();
        String whId = send(post("/api/v1/warehouses"), Map.of("name", "A"), 201)
                .get("id").asText();
        Map<String, Object> mov = new HashMap<>();
        mov.put("partId", partId);
        mov.put("warehouseId", whId);
        mov.put("type", "IN");
        mov.put("quantity", 10);
        send(post("/api/v1/stock/movements"), mov, 201);
        mov.put("type", "OUT_OTHER");
        mov.put("quantity", 8);
        send(post("/api/v1/stock/movements"), mov, 201);

        String meuId = inbox(bearer).get(0).get("id").asText();
        assertThat(inbox(outro)).isEmpty();
        send(outro, post("/api/v1/notifications/" + meuId + "/read"), null, 404);
    }

    @Test
    void requiresASession() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    }
}
