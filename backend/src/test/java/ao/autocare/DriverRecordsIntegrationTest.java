package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.fleet.DriverRecordsService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Motoristas a sério: cartão e exame médico que caducam, infrações com
 * pontos, escala sem sobreposições — e o aviso antes de a viatura ficar
 * parada por causa de um papel.
 */
class DriverRecordsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DriverRecordsService records;

    private String bearer;
    private String motorista;
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

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("motoristas@teste.ao", "Transportes Norte").bearer();
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Kiala Manuel");
        m.put("licenseNumber", "LD-123456");
        m.put("licenseExpiresAt", LocalDate.now().plusYears(2).toString());
        m.put("cardNumber", "CM-9981");
        m.put("cardExpiresAt", LocalDate.now().plusDays(10).toString());
        m.put("medicalExpiresAt", LocalDate.now().minusDays(3).toString());
        motorista = send(post("/api/v1/drivers"), m, 201).get("id").asText();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        camiao = send(post("/api/v1/assets"), Map.of("tag", "CM-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();
    }

    @Test
    void cartaoEExameMedicoContamParaPoderConduzir() throws Exception {
        JsonNode d = send(get("/api/v1/drivers/" + motorista), null, 200);
        assertThat(d.get("cardNumber").asText()).isEqualTo("CM-9981");
        assertThat(d.get("canDrive").asBoolean()).isFalse(); // exame médico caducado há 3 dias
        assertThat(d.get("warnings")).hasSize(2);
        assertThat(d.get("warnings").get(0).asText()).contains("Cartão de motorista caduca em 10");
        assertThat(d.get("warnings").get(1).asText()).contains("Exame médico caducado há 3");

        // O aviso diário chega aos gestores — uma vez por marco.
        int enviados = records.notifyExpiring();
        assertThat(enviados).isGreaterThanOrEqualTo(2);
        JsonNode avisos = send(get("/api/v1/notifications?size=20"), null, 200);
        String texto = avisos.toString();
        assertThat(texto).contains("Exame médico caducado").contains("Cartão de motorista a caducar");
        int outraVez = records.notifyExpiring();
        assertThat(outraVez).isZero();
    }

    @Test
    void infracoesSomamPontosEMultas() throws Exception {
        Map<String, Object> i = new HashMap<>();
        i.put("kind", "SPEEDING");
        i.put("points", 3);
        i.put("fineAmount", 25000);
        i.put("assetId", camiao);
        i.put("description", "132 km/h na EN-100");
        JsonNode criada = send(post("/api/v1/drivers/" + motorista + "/infractions"), i, 201);
        assertThat(criada.get("kindLabel").asText()).isEqualTo("Excesso de velocidade");
        assertThat(criada.get("assetTag").asText()).isEqualTo("CM-1");
        assertThat(criada.get("paid").asBoolean()).isFalse();

        send(post("/api/v1/drivers/" + motorista + "/infractions"),
                Map.of("kind", "ACCIDENT", "points", 5, "occurredAt",
                        Instant.now().minus(400, ChronoUnit.DAYS).toString()), 201);

        JsonNode lista = send(get("/api/v1/drivers/" + motorista + "/infractions"), null, 200);
        assertThat(lista.get("items")).hasSize(2);
        // O acidente tem mais de um ano: não conta para os pontos correntes.
        assertThat(lista.get("pointsLastYear").asInt()).isEqualTo(3);

        String id = criada.get("id").asText();
        assertThat(send(post("/api/v1/driver-infractions/" + id + "/paid"), null, 200).get("paid").asBoolean()).isTrue();
        send(delete("/api/v1/driver-infractions/" + id), null, 200);
        assertThat(send(get("/api/v1/drivers/" + motorista + "/infractions"), null, 200).get("items")).hasSize(1);

        // No futuro não.
        send(post("/api/v1/drivers/" + motorista + "/infractions"),
                Map.of("kind", "FINE", "occurredAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()), 400);
    }

    @Test
    void escalaNaoDeixaDoisTurnosAoMesmoTempo() throws Exception {
        Instant h8 = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS);
        Map<String, Object> t = new HashMap<>();
        t.put("startsAt", h8.toString());
        t.put("endsAt", h8.plus(9, ChronoUnit.HOURS).toString());
        t.put("assetId", camiao);
        t.put("kind", "DAY");
        JsonNode turno = send(post("/api/v1/drivers/" + motorista + "/shifts"), t, 201);
        assertThat(turno.get("kindLabel").asText()).isEqualTo("Diurno");
        assertThat(turno.get("assetTag").asText()).isEqualTo("CM-1");

        // O mesmo motorista, a sobrepor: recusa e diz porquê.
        Map<String, Object> outro = new HashMap<>(t);
        outro.put("startsAt", h8.plus(4, ChronoUnit.HOURS).toString());
        outro.put("endsAt", h8.plus(12, ChronoUnit.HOURS).toString());
        JsonNode erro = send(post("/api/v1/drivers/" + motorista + "/shifts"), outro, 409);
        assertThat(erro.get("message").asText()).contains("já tem turno");

        // Outro motorista na mesma viatura à mesma hora: também não.
        String segundo = send(post("/api/v1/drivers"), Map.of("name", "Pedro"), 201).get("id").asText();
        erro = send(post("/api/v1/drivers/" + segundo + "/shifts"), t, 409);
        assertThat(erro.get("message").asText()).contains("CM-1 já está escalado");

        // Mas depois do turno, pode.
        Map<String, Object> noite = new HashMap<>();
        noite.put("startsAt", h8.plus(10, ChronoUnit.HOURS).toString());
        noite.put("endsAt", h8.plus(18, ChronoUnit.HOURS).toString());
        noite.put("assetId", camiao);
        noite.put("kind", "NIGHT");
        send(post("/api/v1/drivers/" + segundo + "/shifts"), noite, 201);

        JsonNode escala = send(get("/api/v1/drivers/roster"), null, 200);
        assertThat(escala).hasSize(2);
        assertThat(escala.get(0).get("driverName").asText()).isEqualTo("Kiala Manuel");

        // Fim antes do início: erro claro.
        Map<String, Object> mau = new HashMap<>(t);
        mau.put("endsAt", h8.minus(1, ChronoUnit.HOURS).toString());
        send(post("/api/v1/drivers/" + motorista + "/shifts"), mau, 400);
    }
}
