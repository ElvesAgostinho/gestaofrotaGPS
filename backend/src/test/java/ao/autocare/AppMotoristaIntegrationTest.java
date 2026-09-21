package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A aplicação do motorista: o que ela vê, o que ela envia e o que fica no
 * sistema do outro lado.
 *
 * <p>Três coisas se provam aqui. Que o ecrã inicial responde às perguntas do
 * motorista sem ele carregar em nada — a minha viatura, a inspeção de hoje, a
 * rota. Que o <b>telemóvel funciona como aparelho de localização</b>, com as
 * posições a entrarem pelo mesmo caminho das do rastreador da viatura. E que
 * uma ocorrência comunicada da estrada chega ao sistema com as fotografias e o
 * sítio onde aconteceu.
 */
class AppMotoristaIntegrationTest extends AbstractIntegrationTest {

    private String gestor;
    private String motorista;

    private JsonNode send(String bearer, MockHttpServletRequestBuilder req, Object body, int expect)
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

    /** Uma empresa com viatura, motorista com acesso e a viatura atribuída a ele. */
    private String montar(String email, String tag) throws Exception {
        gestor = register(email, "Transportes App").bearer();
        String tipo = send(gestor, post("/api/v1/asset-types"),
                Map.of("name", "Camião basculante", "category", "VEHICLE", "primaryMeter", "ODOMETER"),
                201).get("id").asText();
        String asset = send(gestor, post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Camião", "assetTypeId", tipo, "initialMeterValue", 120_000),
                201).get("id").asText();
        String driver = send(gestor, post("/api/v1/drivers"),
                Map.of("name", "Joaquim Manuel", "phone", "+244 923 111 222",
                        "licenseNumber", "AO-99" + tag), 201).get("id").asText();
        JsonNode cred = send(gestor, post("/api/v1/drivers/" + driver + "/access"), null, 201);
        send(gestor, post("/api/v1/driver-assignments"),
                Map.of("driverId", driver, "assetId", asset, "primaryDriver", true), 201);

        JsonNode login = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", cred.get("loginId").asText(),
                        "password", cred.get("password").asText()), 200);
        motorista = "Bearer " + login.get("accessToken").asText();
        return asset;
    }

    @Test
    void oEcraInicialRespondeAsPerguntasDoMotorista() throws Exception {
        String asset = montar("app1@teste.ao", "CAM-1");

        JsonNode home = send(motorista, get("/api/v1/mobile/home"), null, 200);
        assertThat(home.get("userName").asText()).contains("Joaquim");
        assertThat(home.get("hasAssignedAssets").asBoolean()).isTrue();

        JsonNode minhas = home.get("myAssets");
        assertThat(minhas).hasSize(1);
        JsonNode minha = minhas.get(0);
        assertThat(minha.get("tag").asText()).isEqualTo("CAM-1");
        assertThat(minha.get("meterLabel").asText()).contains("km");
        // Ainda não fez a inspeção de hoje — e o ecrã tem de o dizer.
        assertThat(minha.get("inspectionDoneToday").asBoolean()).isFalse();
        assertThat(home.get("warnings").toString()).contains("inspeção diária por fazer");

        // Depois de a fazer, deixa de aparecer como pendente.
        send(motorista, post("/api/v1/assets/" + asset + "/checklist-executions"), Map.of(
                "templateName", "Inspeção diária",
                "items", List.of(Map.of("text", "Nível do óleo do motor", "result", "OK"))), 201);
        JsonNode depois = send(motorista, get("/api/v1/mobile/home"), null, 200);
        assertThat(depois.get("myAssets").get(0).get("inspectionDoneToday").asBoolean()).isTrue();
    }

    @Test
    void oTelemovelEnviaPosicoesQueEntramNoMapaDaEmpresa() throws Exception {
        String asset = montar("app2@teste.ao", "CAM-2");

        Instant agora = Instant.now();
        JsonNode r = send(motorista, post("/api/v1/mobile/positions"), Map.of(
                "assetId", asset,
                "positions", List.of(
                        Map.of("latitude", -8.8383, "longitude", 13.2344, "speedKph", 42,
                                "accuracyM", 8, "recordedAt", agora.minusSeconds(120).toString()),
                        Map.of("latitude", -8.8400, "longitude", 13.2400, "speedKph", 51,
                                "accuracyM", 6, "recordedAt", agora.minusSeconds(60).toString()))), 200);
        assertThat(r.get("accepted").asInt()).isEqualTo(2);

        // O gestor vê a viatura no mapa ao vivo, com a posição que veio do telemóvel.
        JsonNode vivo = send(gestor, get("/api/v1/telemetry/live"), null, 200);
        assertThat(vivo.toString()).contains("CAM-2");
        assertThat(vivo.toString()).contains("13.24");
    }

    @Test
    void naoSeEnviamPosicoesDeUmaViaturaQueNaoEMinha() throws Exception {
        montar("app3@teste.ao", "CAM-3");
        String outra = send(gestor, post("/api/v1/assets"), Map.of(
                "tag", "CAM-3B", "name", "Outro camião",
                "assetTypeId", send(gestor, get("/api/v1/asset-types"), null, 200).get(0).get("id").asText()),
                201).get("id").asText();

        send(motorista, post("/api/v1/mobile/positions"), Map.of(
                "assetId", outra,
                "positions", List.of(Map.of("latitude", -8.8, "longitude", 13.2))), 403);
    }

    @Test
    void aRotaDeHojeChegaAoTelemovelComOTracado() throws Exception {
        String asset = montar("app4@teste.ao", "CAM-4");

        // Uma rota nasce sempre com viatura: é a regra do sistema.
        send(gestor, post("/api/v1/routes"), Map.of(
                "name", "Luanda - Lobito", "code", "LAD-LOB",
                "originLabel", "Luanda", "destinationLabel", "Lobito",
                "expectedDistanceKm", 528, "expectedDurationMinutes", 482,
                "assetId", asset,
                "plannedFor", LocalDate.now().toString(),
                "waypoints", List.of(
                        Map.of("label", "Luanda", "latitude", -8.9059, "longitude", 13.3709),
                        Map.of("label", "Lobito", "latitude", -12.3507, "longitude", 13.5464))), 201);

        JsonNode noTelemovel = send(motorista, get("/api/v1/mobile/route"), null, 200);
        assertThat(noTelemovel.get("name").asText()).isEqualTo("Luanda - Lobito");
        assertThat(noTelemovel.get("points")).hasSize(2);
        assertThat(noTelemovel.get("points").get(0).get("tipo").asText()).isEqualTo("origem");
        assertThat(noTelemovel.get("points").get(1).get("tipo").asText()).isEqualTo("destino");

        // E aparece no ecrã inicial, para ele não ter de a procurar.
        JsonNode home = send(motorista, get("/api/v1/mobile/home"), null, 200);
        assertThat(home.get("route").get("name").asText()).isEqualTo("Luanda - Lobito");
    }

    @Test
    void umaOcorrenciaLevaFotografiasEOLocalOndeAconteceu() throws Exception {
        String asset = montar("app5@teste.ao", "CAM-5");

        MockMultipartFile foto1 = new MockMultipartFile("photos", "acidente-1.jpg",
                "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
        MockMultipartFile foto2 = new MockMultipartFile("photos", "acidente-2.jpg",
                "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 4, 5, 6});

        MvcResult r = mvc.perform(multipart("/api/v1/mobile/occurrences")
                        .file(foto1).file(foto2)
                        .param("assetId", asset)
                        .param("title", "Colisão na traseira")
                        .param("kind", "ACIDENTE")
                        .param("description", "Na estrada de Catete, ao km 40.")
                        .param("latitude", "-9.1032")
                        .param("longitude", "13.6889")
                        .param("stopped", "true")
                        .header("Authorization", motorista))
                .andExpect(status().isCreated()).andReturn();
        JsonNode ordem = json.readTree(r.getResponse().getContentAsByteArray());

        assertThat(ordem.get("title").asText()).startsWith("Acidente:");
        JsonNode ficha = send(gestor, get("/api/v1/work-orders/" + ordem.get("id").asText()), null, 200);
        assertThat(ficha.get("description").asText()).contains("Local: -9.1032, 13.6889");

        JsonNode anexos = send(gestor,
                get("/api/v1/work-orders/" + ordem.get("id").asText() + "/attachments"), null, 200);
        assertThat(anexos).hasSize(2);

        // E o gestor é avisado de que a viatura ficou parada.
        JsonNode avisos = send(gestor, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos.toString()).contains("Viatura parada: CAM-5");
    }
}
