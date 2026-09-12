package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Motoristas, filiais e rotas.
 *
 * <p>O que se testa a sério aqui é a <b>atribuição no tempo</b>: é ela que
 * permite dizer, meses depois, quem conduzia o camião no dia do excesso de
 * velocidade. Um campo "motorista" no ativo passaria nos testes fáceis e
 * mentiria sobre o passado à primeira troca de condutor.
 */
class FleetIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String assetId;
    private String otherAssetId;

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
        bearer = register("frota@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camiao"), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-001", "name", "Camiao Volvo", "assetTypeId", typeId), 201)
                .get("id").asText();
        otherAssetId = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-002", "name", "Camiao Scania", "assetTypeId", typeId), 201)
                .get("id").asText();
    }

    private Map<String, Object> motorista(String nome, String numero) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", nome);
        body.put("employeeNumber", numero);
        body.put("phone", "+244923000111");
        body.put("licenseNumber", "LIC-" + numero);
        body.put("licenseCategories", "B, C");
        body.put("licenseExpiresAt", LocalDate.now().plusYears(2).toString());
        return body;
    }

    private String criarMotorista(String nome, String numero) throws Exception {
        return send(post("/api/v1/drivers"), motorista(nome, numero), 201).get("id").asText();
    }

    // ---- filiais ----------------------------------------------------------
    @Test
    void aBranchIsALocationWithCostCentreAndCoordinates() throws Exception {
        Map<String, Object> filial = new HashMap<>();
        filial.put("name", "Filial de Benguela");
        filial.put("kind", "BRANCH");
        filial.put("costCenter", "CC-BG-01");
        filial.put("city", "Benguela");
        filial.put("province", "Benguela");
        filial.put("latitude", -12.5763);
        filial.put("longitude", 13.4055);
        filial.put("radiusMeters", 300);

        JsonNode criada = send(post("/api/v1/locations"), filial, 201);
        assertThat(criada.get("kind").asText()).isEqualTo("BRANCH");
        assertThat(criada.get("costCenter").asText()).isEqualTo("CC-BG-01");
        assertThat(criada.get("radiusMeters").asInt()).isEqualTo(300);

        // Continua a ser um local: a hierarquia e os ativos funcionam como antes.
        Map<String, Object> parque = new HashMap<>();
        parque.put("name", "Parque de maquinas");
        parque.put("kind", "YARD");
        parque.put("parentId", criada.get("id").asText());
        JsonNode filho = send(post("/api/v1/locations"), parque, 201);
        assertThat(filho.get("parentName").asText()).isEqualTo("Filial de Benguela");
    }

    @Test
    void aDriverCannotBeAttachedToALocationThatIsNotABranch() throws Exception {
        JsonNode armazem = send(post("/api/v1/locations"),
                Map.of("name", "Armazem central", "kind", "WAREHOUSE"), 201);

        Map<String, Object> body = motorista("Joao Baptista", "F-001");
        body.put("branchId", armazem.get("id").asText());

        assertThat(send(post("/api/v1/drivers"), body, 400).get("message").asText())
                .contains("não é uma filial");
    }

    // ---- motoristas -------------------------------------------------------
    @Test
    void aDriverDoesNotNeedASystemAccount() throws Exception {
        JsonNode d = send(post("/api/v1/drivers"), motorista("Adao Ferreira", "F-100"), 201);

        assertThat(d.get("name").asText()).isEqualTo("Adao Ferreira");
        // Sem conta no sistema — que é o caso normal em quase todas as frotas.
        assertThat(d.hasNonNull("userId")).isFalse();
        assertThat(d.get("canDrive").asBoolean()).isTrue();
        assertThat(d.get("statusLabel").asText()).isEqualTo("Ativo");
    }

    @Test
    void theEmployeeNumberIsUniqueWithinTheCompany() throws Exception {
        criarMotorista("Primeiro", "F-200");
        assertThat(send(post("/api/v1/drivers"), motorista("Segundo", "F-200"), 409)
                .get("message").asText()).contains("F-200");
    }

    @Test
    void theServerSaysHowManyDaysUntilTheLicenceExpires() throws Exception {
        Map<String, Object> body = motorista("Carta a expirar", "F-300");
        body.put("licenseExpiresAt", LocalDate.now().plusDays(10).toString());
        JsonNode d = send(post("/api/v1/drivers"), body, 201);

        // A conta vem do servidor: se cada ecra a fizesse, bastava um fuso
        // diferente para dois ecras discordarem sobre se a carta e valida.
        assertThat(d.get("licenseExpiresInDays").asInt()).isEqualTo(10);
        assertThat(d.get("licenseExpired").asBoolean()).isFalse();

        JsonNode aviso = send(get("/api/v1/drivers/licenses"), null, 200);
        assertThat(aviso).hasSize(1);
    }

    @Test
    void anExpiredLicenceIsRefusedAtAssignmentTime() throws Exception {
        Map<String, Object> body = motorista("Carta caducada", "F-400");
        body.put("licenseExpiresAt", LocalDate.now().minusDays(3).toString());
        String driverId = send(post("/api/v1/drivers"), body, 201).get("id").asText();

        JsonNode d = send(get("/api/v1/drivers/" + driverId), null, 200);
        assertThat(d.get("licenseExpired").asBoolean()).isTrue();
        assertThat(d.get("canDrive").asBoolean()).isFalse();

        // A empresa responde por deixar conduzir quem nao pode. O sistema nao
        // pode ser o sitio onde isso fica registado como normal.
        assertThat(send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 409)
                .get("message").asText()).contains("caducou");
    }

    @Test
    void aSuspendedDriverCannotBeAssigned() throws Exception {
        String driverId = criarMotorista("Suspenso", "F-500");
        Map<String, Object> alterado = motorista("Suspenso", "F-500");
        alterado.put("status", "SUSPENDED");
        send(put("/api/v1/drivers/" + driverId), alterado, 200);

        assertThat(send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 409)
                .get("message").asText()).contains("suspenso");
    }

    // ---- atribuicoes ------------------------------------------------------
    @Test
    void assigningANewPrimaryDriverClosesThePreviousOne() throws Exception {
        String primeiro = criarMotorista("Motorista Um", "F-600");
        String segundo = criarMotorista("Motorista Dois", "F-601");

        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", primeiro, "assetId", assetId), 201);
        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", segundo, "assetId", assetId), 201);

        JsonNode historico = send(get("/api/v1/assets/" + assetId + "/drivers"), null, 200);
        assertThat(historico).hasSize(2);

        // O anterior fecha no instante em que o novo comeca: sem intervalo por
        // explicar entre os dois, e sem dois titulares ao mesmo tempo.
        long abertas = 0;
        for (JsonNode a : historico) {
            if (a.get("open").asBoolean()) {
                abertas++;
                assertThat(a.get("driverName").asText()).isEqualTo("Motorista Dois");
            }
        }
        assertThat(abertas).isEqualTo(1);
    }

    @Test
    void aSecondaryDriverDoesNotDisplaceTheTitular() throws Exception {
        String titular = criarMotorista("Titular", "F-700");
        String ajudante = criarMotorista("Ajudante", "F-701");

        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", titular, "assetId", assetId), 201);

        Map<String, Object> secundario = new HashMap<>();
        secundario.put("driverId", ajudante);
        secundario.put("assetId", assetId);
        secundario.put("primaryDriver", false);
        send(post("/api/v1/driver-assignments"), secundario, 201);

        JsonNode historico = send(get("/api/v1/assets/" + assetId + "/drivers"), null, 200);
        long abertas = 0;
        for (JsonNode a : historico) {
            if (a.get("open").asBoolean()) abertas++;
        }
        assertThat(abertas).isEqualTo(2);
    }

    @Test
    void theSameDriverCannotBeAssignedTwiceToTheSameAsset() throws Exception {
        String driverId = criarMotorista("Repetido", "F-800");
        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 201);

        assertThat(send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 409)
                .get("message").asText()).contains("já está atribuído");
    }

    @Test
    void oneDriverCanHoldTwoAssetsAtOnce() throws Exception {
        String driverId = criarMotorista("Dois ativos", "F-810");
        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 201);
        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", otherAssetId), 201);

        JsonNode d = send(get("/api/v1/drivers/" + driverId), null, 200);
        assertThat(d.get("currentAssets")).hasSize(2);
    }

    @Test
    void anAssignmentCanBeClosedAndTheHistoryStays() throws Exception {
        String driverId = criarMotorista("Saiu", "F-900");
        String assignmentId = send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 201).get("id").asText();

        JsonNode fechada = send(
                patch("/api/v1/driver-assignments/" + assignmentId + "/end"), null, 200);
        assertThat(fechada.get("open").asBoolean()).isFalse();
        assertThat(fechada.hasNonNull("endedAt")).isTrue();

        // Fechar duas vezes nao faz sentido e diz-se porque.
        send(patch("/api/v1/driver-assignments/" + assignmentId + "/end"), null, 409);

        assertThat(send(get("/api/v1/assets/" + assetId + "/drivers"), null, 200)).hasSize(1);
    }

    @Test
    void aDriverWithHistoryIsNotDeleted() throws Exception {
        String driverId = criarMotorista("Com historico", "F-950");
        send(post("/api/v1/driver-assignments"),
                Map.of("driverId", driverId, "assetId", assetId), 201);

        // Apagar levaria o historico de quem conduzia o que -- e e esse
        // historico que responde por infracoes e consumos ja registados.
        assertThat(send(delete("/api/v1/drivers/" + driverId), null, 409)
                .get("message").asText()).contains("inativo");
    }

    // ---- rotas ------------------------------------------------------------
    @Test
    void aRouteNeedsAnOriginAndADestination() throws Exception {
        assertThat(send(post("/api/v1/routes"),
                Map.of("name", "Rota sem pontas"), 400)
                .get("message").asText()).contains("origem e destino");
    }

    @Test
    void aRouteCarriesTheExpectedFiguresToCompareAgainst() throws Exception {
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Luanda - Lobito");
        rota.put("code", "LAD-LOB");
        rota.put("originLabel", "Luanda");
        rota.put("destinationLabel", "Lobito");
        rota.put("expectedDistanceKm", 480);
        rota.put("expectedDurationMinutes", 420);
        rota.put("expectedFuelLiters", 160);
        rota.put("tolerancePercent", 10);
        rota.put("waypoints", List.of(
                Map.of("label", "Sumbe"),
                Map.of("label", "Porto Amboim")));

        JsonNode r = send(post("/api/v1/routes"), rota, 201);
        assertThat(r.get("originName").asText()).isEqualTo("Luanda");
        assertThat(r.get("destinationName").asText()).isEqualTo("Lobito");
        assertThat(r.get("expectedDistanceKm").asDouble()).isEqualTo(480.0);
        assertThat(r.get("waypoints")).hasSize(2);
        assertThat(r.get("waypoints").get(0).get("sortOrder").asInt()).isEqualTo(0);
    }

    @Test
    void aRouteWithoutExpectedFuelSaysNothingInsteadOfGuessing() throws Exception {
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Rota nova");
        rota.put("originLabel", "A");
        rota.put("destinationLabel", "B");
        JsonNode r = send(post("/api/v1/routes"), rota, 201);

        // Preencher um palpite daria ar de rigor a um numero inventado.
        assertThat(r.hasNonNull("expectedFuelLiters")).isFalse();
        // A tolerancia tem valor por omissao, que e diferente de inventar dados.
        assertThat(r.get("tolerancePercent").asDouble()).isEqualTo(15.0);
    }

    @Test
    void routeCodesAreUnique() throws Exception {
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Rota A");
        rota.put("code", "R-1");
        rota.put("originLabel", "A");
        rota.put("destinationLabel", "B");
        send(post("/api/v1/routes"), rota, 201);

        rota.put("name", "Rota B");
        assertThat(send(post("/api/v1/routes"), rota, 409).get("message").asText())
                .contains("R-1");
    }
}
