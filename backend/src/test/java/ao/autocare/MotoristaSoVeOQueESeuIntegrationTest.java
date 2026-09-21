package ao.autocare;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Um motorista vê o que é dele — e mais nada.
 *
 * <p>Esta falha existiu e foi apanhada por quem usa o sistema: as permissões
 * travavam quem <b>escreve</b>, mas qualquer pessoa autenticada podia
 * <b>ler</b> a empresa inteira. No telemóvel não se notava; bastava trocar
 * para a versão completa para ver todos os veículos, os custos, os
 * indicadores e a equipa.
 *
 * <p>O que este teste protege é a fronteira: o que o motorista precisa
 * continua a funcionar, e tudo o resto responde 403. Uma falha destas numa
 * demonstração a um cliente vale mais do que qualquer funcionalidade.
 */
class MotoristaSoVeOQueESeuIntegrationTest extends AbstractIntegrationTest {

    private String gestor;
    private String motorista;
    private String assetId;

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

    private void montar(String email) throws Exception {
        gestor = register(email, "Construções Fronteira").bearer();
        String tipo = send(gestor, post("/api/v1/asset-types"),
                Map.of("name", "Camião basculante", "category", "VEHICLE", "primaryMeter", "ODOMETER"),
                201).get("id").asText();
        assetId = send(gestor, post("/api/v1/assets"),
                Map.of("tag", "CAM-F1", "name", "Camião", "assetTypeId", tipo,
                        "initialMeterValue", 100_000), 201).get("id").asText();
        String driverId = send(gestor, post("/api/v1/drivers"),
                Map.of("name", "Joaquim Fronteira", "phone", "+244 923 555 111",
                        "licenseNumber", "AO-FRONT-1"), 201).get("id").asText();
        JsonNode cred = send(gestor, post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        send(gestor, post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId, "primaryDriver", true), 201);
        JsonNode login = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", cred.get("loginId").asText(),
                        "password", cred.get("password").asText()), 200);
        motorista = "Bearer " + login.get("accessToken").asText();
    }

    @Test
    void oMotoristaNaoVeAEmpresaInteira() throws Exception {
        montar("fronteira1@teste.ao");

        // O que ele nunca devia ter visto — e via.
        send(motorista, get("/api/v1/assets"), null, 403);
        send(motorista, get("/api/v1/assets/" + assetId), null, 403);
        send(motorista, get("/api/v1/work-orders"), null, 403);
        send(motorista, get("/api/v1/dashboard"), null, 403);
        send(motorista, get("/api/v1/kpis"), null, 403);
        send(motorista, get("/api/v1/telemetry/live"), null, 403);
        send(motorista, get("/api/v1/drivers"), null, 403);
        send(motorista, get("/api/v1/routes"), null, 403);
        send(motorista, get("/api/v1/maintenance-plans"), null, 403);
        send(motorista, get("/api/v1/parts"), null, 403);
        send(motorista, get("/api/v1/audit"), null, 403);
        send(motorista, get("/api/v1/predictive"), null, 403);
        send(motorista, get("/api/v1/gps-devices"), null, 403);

        // E continua sem poder mexer em nada — como já era.
        send(motorista, post("/api/v1/assets"), Map.of("tag", "X", "name", "X"), 403);
        send(motorista, post("/api/v1/team/invitations"),
                Map.of("email", "outro@teste.ao", "role", "OWNER"), 403);
    }

    @Test
    void oMotoristaContinuaAPoderFazerOTrabalhoDele() throws Exception {
        montar("fronteira2@teste.ao");

        // O ecrã inicial da app, a rota e o perfil.
        send(motorista, get("/api/v1/mobile/home"), null, 200);
        send(motorista, get("/api/v1/mobile/route"), null, 200);
        send(motorista, get("/api/v1/auth/me"), null, 200);
        send(motorista, get("/api/v1/notifications"), null, 200);

        // A inspeção diária: ver a folha e registá-la.
        send(motorista, get("/api/v1/checklist-templates"), null, 200);
        send(motorista, post("/api/v1/assets/" + assetId + "/checklist-executions"), Map.of(
                "templateName", "Inspeção diária",
                "items", List.of(Map.of("text", "Nível do óleo do motor", "result", "OK"))), 201);

        // Abastecer, atestar e comunicar uma avaria.
        send(motorista, post("/api/v1/assets/" + assetId + "/fuel"),
                Map.of("liters", 120, "meterValue", 100_200), 201);
        send(motorista, post("/api/v1/assets/" + assetId + "/fluid-topups"),
                Map.of("kind", "COOLANT", "liters", 1), 201);

        // E enviar a posição do telemóvel durante a viagem.
        send(motorista, post("/api/v1/mobile/positions"), Map.of(
                "assetId", assetId,
                "positions", List.of(Map.of("latitude", -8.84, "longitude", 13.23))), 200);
    }

    @Test
    void oTecnicoEOGestorContinuamAVerAFrota() throws Exception {
        montar("fronteira3@teste.ao");
        send(gestor, get("/api/v1/assets"), null, 200);
        send(gestor, get("/api/v1/dashboard"), null, 200);
        send(gestor, get("/api/v1/kpis"), null, 200);
        send(gestor, get("/api/v1/drivers"), null, 200);
    }
}
