package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A inspeção diária de cada máquina e a ficha do posto.
 *
 * <p>Quem compra o sistema faz sempre a mesma pergunta: «e a folha que está
 * pendurada na cabina, onde é que está?». Está aqui: o sistema sabe o que se
 * verifica numa retroescavadora antes do arranque mesmo antes de a empresa
 * escrever seja o que for, cria o modelo num clique, e imprime a folha com o
 * timbre da casa — inspeção, intervalos de lubrificação e materiais.
 */
class DailyInspectionIntegrationTest extends AbstractIntegrationTest {

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

    private static String texto(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace(' ', ' ');
        }
    }

    @Test
    void sugereAInspecaoDoCatalogoEAdoptaANumClique() throws Exception {
        bearer = register("insp1@teste.ao", "Construções Inspeção").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Retroescavadora"), 201).get("id").asText();
        String ativo = send(post("/api/v1/assets"),
                Map.of("tag", "RE-9", "name", "Retroescavadora", "assetTypeId", tipo), 201).get("id").asText();

        // Sem nada criado, o sistema já sabe o que se verifica nesta família —
        // e diz que é sugestão, não um modelo da empresa.
        JsonNode sugerida = send(get("/api/v1/assets/" + ativo + "/daily-inspection"), null, 200);
        assertThat(sugerida.get("source").asText()).isEqualTo("SUGGESTED");
        assertThat(sugerida.hasNonNull("templateId")).isFalse();
        assertThat(sugerida.get("estimatedMinutes").asInt()).isEqualTo(15);
        assertThat(sugerida.get("items")).hasSizeGreaterThan(8);
        assertThat(sugerida.get("items").toString()).contains("Nível do óleo do motor");

        // Um clique cria-a mesmo, com o tipo de equipamento já preenchido.
        JsonNode criada = send(post("/api/v1/assets/" + ativo + "/daily-inspection"), null, 201);
        assertThat(criada.get("items")).hasSizeGreaterThan(8);

        JsonNode propria = send(get("/api/v1/assets/" + ativo + "/daily-inspection"), null, 200);
        assertThat(propria.get("source").asText()).isEqualTo("OWN");
        assertThat(propria.get("templateId").asText()).isEqualTo(criada.get("id").asText());

        // Criar outra vez não duplica a folha.
        send(post("/api/v1/assets/" + ativo + "/daily-inspection"), null, 409);
    }

    @Test
    void aFichaDoPostoLevaInspecaoIntervalosEMateriais() throws Exception {
        bearer = register("insp2@teste.ao", "Oficina do Posto").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Retroescavadora"), 201).get("id").asText();
        String ativo = send(post("/api/v1/assets"), Map.of("tag", "RE-8", "name", "Retroescavadora BL71B",
                "assetTypeId", tipo, "initialMeterValue", 1240), 201).get("id").asText();

        String plano = send(post("/api/v1/maintenance-plans"), Map.of(
                "name", "Plano preventivo BL71B",
                "tasks", List.of(Map.of(
                        "systemName", "Lubrificação", "title", "Lubrificar articulações e pinos",
                        "tools", "Bomba de massa, massa EP2",
                        "triggers", List.of(Map.of("type", "METER_INTERVAL",
                                "meterKind", "HOURMETER", "interval", 50))))), 201).get("id").asText();
        send(post("/api/v1/assets/" + ativo + "/maintenance-plans"), Map.of("planId", plano), 201);

        MvcResult r = mvc.perform(get("/api/v1/assets/" + ativo + "/operator-sheet.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        byte[] pdf = r.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        String t = texto(pdf).replace(' ', ' ');
        assertThat(t).containsIgnoringCase("Inspeção diária");
        assertThat(t).containsIgnoringCase("Tempo estimado");
        assertThat(t).contains("Nível do óleo do motor");
        assertThat(t).containsIgnoringCase("A cada 50 horas");
        assertThat(t).contains("Lubrificar articulações e pinos");
        assertThat(t).containsIgnoringCase("Ferramentas e materiais");
        assertThat(t).contains("Bomba de massa, massa EP2");
        assertThat(t).contains("RE-8");
    }

    @Test
    void aFichaSaiMesmoSemPlanoESemModeloCriado() throws Exception {
        bearer = register("insp3@teste.ao", "Energia Sem Plano").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Gerador"), 201).get("id").asText();
        String ativo = send(post("/api/v1/assets"),
                Map.of("tag", "GER-9", "name", "Gerador", "assetTypeId", tipo), 201).get("id").asText();

        MvcResult r = mvc.perform(get("/api/v1/assets/" + ativo + "/operator-sheet.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        String t = texto(r.getResponse().getContentAsByteArray());
        assertThat(t).containsIgnoringCase("plano de manutenção");
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/v1/assets/x/daily-inspection")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/assets/x/operator-sheet.pdf")).andExpect(status().isUnauthorized());
    }
}
