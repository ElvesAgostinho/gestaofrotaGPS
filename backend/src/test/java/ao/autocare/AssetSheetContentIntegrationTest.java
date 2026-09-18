package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.tyre.TyreService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A ficha da viatura em PDF leva o que a oficina precisa: o plano com os
 * intervalos (última vez, próxima, quanto falta), os pneus montados e os
 * documentos com validade. E os pneus com problema avisam quem gere e
 * aparecem no «Hoje».
 */
class AssetSheetContentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TyreService tyreService;

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
            return new PDFTextStripper().getText(doc).replace(' ', ' ');
        }
    }

    @Test
    void aFichaTemPlanoPneusEDocumentosEOsPneusComAlertaAvisam() throws Exception {
        bearer = register("ficha@teste.ao", "Transportes Ficha").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201).get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of("tag", "FC-1", "name", "Camião Ficha", "assetTypeId", tipo,
                "initialMeterValue", 48_000), 201).get("id").asText();

        // Mudança de óleo a cada 5 000 km; a última aos 46 000 → próxima aos 51 000 (faltam 3 000).
        Map<String, Object> intervalo = new HashMap<>();
        intervalo.put("title", "Mudança de óleo");
        intervalo.put("everyKm", 5000);
        intervalo.put("lastDoneMeter", 46_000);
        intervalo.put("lastDoneAt", Instant.now().minus(40, ChronoUnit.DAYS).toString());
        send(post("/api/v1/assets/" + assetId + "/maintenance-interval"), intervalo, 201);

        // Um pneu montado, com sulco abaixo do mínimo.
        Map<String, Object> pneu = new HashMap<>();
        pneu.put("position", "FE");
        pneu.put("brand", "Michelin");
        pneu.put("size", "315/80 R22.5");
        pneu.put("lastTreadMm", 16);
        String pneuId = send(post("/api/v1/assets/" + assetId + "/tyres"), pneu, 201).get("id").asText();
        send(post("/api/v1/tyres/" + pneuId + "/readings"), Map.of("treadMm", 2.5), 201);

        // Um seguro a caducar em 10 dias.
        Map<String, Object> doc = new HashMap<>();
        doc.put("title", "Seguro automóvel");
        doc.put("kind", "INSURANCE");
        doc.put("reference", "AP-2026-77");
        doc.put("expiresAt", Instant.now().plus(10, ChronoUnit.DAYS).toString());
        send(post("/api/v1/assets/" + assetId + "/documents"), doc, 201);

        String ficha = texto(mvc.perform(get("/api/v1/assets/" + assetId + "/sheet.pdf").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(ficha).containsIgnoringCase("Plano de manutenção e intervalos");
        assertThat(ficha).contains("Mudança de óleo").contains("cada 5 000 km").contains("46 000").contains("51 000").contains("faltam 3 000 km");
        assertThat(ficha).containsIgnoringCase("Pneus montados").contains("FE").contains("Michelin").contains("2,5 mm").contains("Sulco abaixo do mínimo");
        assertThat(ficha).containsIgnoringCase("Documentos e validades").contains("Seguro automóvel").contains("AP-2026-77").contains("A caducar");

        // O pneu com problema avisa quem gere — uma vez — e aparece no painel de hoje.
        assertThat(tyreService.notifyAlerts()).isEqualTo(1);
        assertThat(tyreService.notifyAlerts()).isZero();
        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).anySatisfy(n -> {
            assertThat(n.get("title").asText()).isEqualTo("Pneu com alerta — FC-1 FE");
            assertThat(n.get("category").asText()).isEqualTo("TIRE");
        });
        JsonNode hoje = send(get("/api/v1/dashboard"), null, 200).get("today");
        boolean temPneus = false;
        for (JsonNode g : hoje.get("groups")) {
            if (g.get("key").asText().equals("tyres")) {
                temPneus = true;
                assertThat(g.get("items").get(0).get("title").asText()).isEqualTo("Pneu FE");
            }
        }
        assertThat(temPneus).isTrue();

        // Trocado o pneu, o histórico regista a troca com os km feitos.
        send(post("/api/v1/tyres/" + pneuId + "/remove"), Map.of("reason", "WORN"), 200);
        String historico = texto(mvc.perform(get("/api/v1/assets/" + assetId + "/history.pdf").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(historico).containsIgnoringCase("Pneus: montagens e trocas").contains("Michelin").contains("Desgaste");
        assertThat(tyreService.notifyAlerts()).isZero();
    }
}
