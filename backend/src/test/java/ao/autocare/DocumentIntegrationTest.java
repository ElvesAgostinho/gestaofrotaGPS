package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.document.DocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Documentos do ativo: validade, avisos e ficheiros. */
class DocumentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DocumentService documentService;

    private String bearer;
    private String assetId;

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

    private void setUpAsset(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
    }

    private JsonNode docs() throws Exception {
        return send(get("/api/v1/assets/" + assetId + "/documents"), null, 200);
    }

    private JsonNode inbox() throws Exception {
        return send(get("/api/v1/notifications"), null, 200).get("content");
    }

    /** Cria um documento com validade daqui a N dias (negativo = já caducou). */
    private String documentDueIn(String title, String kind, long days) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("kind", kind);
        body.put("expiresAt", Instant.now().plus(days, ChronoUnit.DAYS).toString());
        return send(post("/api/v1/assets/" + assetId + "/documents"), body, 201)
                .get("id").asText();
    }

    // ---- validade ------------------------------------------------------
    @Test
    void classifiesDocumentsByHowCloseTheyAreToExpiring() throws Exception {
        setUpAsset("dc1@teste.ao", "CAM-001");

        documentDueIn("Apólice 2027", "INSURANCE", 200);   // longe
        documentDueIn("Inspeção periódica", "INSPECTION", 10);  // a chegar
        documentDueIn("Licença de transporte", "LICENSE", -5);  // já caducou
        // Um manual não caduca.
        send(post("/api/v1/assets/" + assetId + "/documents"),
                Map.of("title", "Manual do operador", "kind", "MANUAL"), 201);

        Map<String, String> estados = new HashMap<>();
        for (JsonNode d : docs()) {
            estados.put(d.get("title").asText(), d.get("state").asText());
        }
        assertThat(estados).containsEntry("Apólice 2027", "VALIDO");
        assertThat(estados).containsEntry("Inspeção periódica", "A_CADUCAR");
        assertThat(estados).containsEntry("Licença de transporte", "CADUCADO");
        assertThat(estados).containsEntry("Manual do operador", "SEM_VALIDADE");

        JsonNode inspecao = null;
        for (JsonNode d : docs()) {
            if ("Inspeção periódica".equals(d.get("title").asText())) inspecao = d;
        }
        assertThat(inspecao.get("kindLabel").asText()).isEqualTo("Inspeção");
        assertThat(inspecao.get("expiryLabel").asText()).contains("Caduca em");
        assertThat(inspecao.get("daysRemaining").asLong()).isBetween(9L, 10L);
    }

    @Test
    void theFleetViewShowsWhatIsAboutToExpireExpiredFirst() throws Exception {
        setUpAsset("dc2@teste.ao", "CAM-002");
        documentDueIn("Seguro caducado", "INSURANCE", -3);
        documentDueIn("Inspeção", "INSPECTION", 20);
        documentDueIn("Licença longínqua", "LICENSE", 300);

        JsonNode aCaducar = send(get("/api/v1/documents/expiring?withinDays=60"), null, 200);
        assertThat(aCaducar).hasSize(2);
        // O já caducado vem primeiro: é o mais urgente.
        assertThat(aCaducar.get(0).get("title").asText()).isEqualTo("Seguro caducado");
        assertThat(aCaducar.get(0).get("state").asText()).isEqualTo("CADUCADO");
        assertThat(aCaducar.get(0).get("assetTag").asText()).isEqualTo("CAM-002");

        // Alargando a janela, entra também o distante.
        assertThat(send(get("/api/v1/documents/expiring?withinDays=400"), null, 200)).hasSize(3);
    }

    @Test
    void refusesAnExpiryDateBeforeTheIssueDate() throws Exception {
        setUpAsset("dc3@teste.ao", "CAM-003");
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Apólice mal preenchida");
        body.put("kind", "INSURANCE");
        body.put("issuedAt", Instant.now().toString());
        body.put("expiresAt", Instant.now().minus(10, ChronoUnit.DAYS).toString());

        assertThat(send(post("/api/v1/assets/" + assetId + "/documents"), body, 400)
                .get("message").asText()).contains("posterior à data de emissão");
    }

    // ---- avisos ---------------------------------------------------------
    @Test
    void warnsAtMilestonesInsteadOfEveryDay() throws Exception {
        setUpAsset("dc4@teste.ao", "CAM-004");
        documentDueIn("Seguro", "INSURANCE", 5);        // dentro do marco de 7
        documentDueIn("Licença", "LICENSE", 100);       // ainda longe

        assertThat(documentService.notifyExpiring()).isEqualTo(1);
        JsonNode avisos = inbox();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("category").asText()).isEqualTo("DOCUMENT");
        assertThat(avisos.get(0).get("title").asText()).contains("Seguro a caducar");

        // Correr outra vez no mesmo marco não repete o aviso.
        assertThat(documentService.notifyExpiring()).isZero();
        assertThat(inbox()).hasSize(1);
    }

    @Test
    void anExpiredDocumentWarnsAsCritical() throws Exception {
        setUpAsset("dc5@teste.ao", "CAM-005");
        documentDueIn("Inspeção periódica", "INSPECTION", -2);

        documentService.notifyExpiring();
        JsonNode avisos = inbox();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(avisos.get(0).get("title").asText()).contains("caducado");
    }

    @Test
    void renewingADocumentClearsItsWarning() throws Exception {
        setUpAsset("dc6@teste.ao", "CAM-006");
        String docId = documentDueIn("Seguro", "INSURANCE", 3);

        documentService.notifyExpiring();
        assertThat(inbox()).hasSize(1);

        // Renovação: nova validade daqui a um ano.
        send(patch("/api/v1/documents/" + docId),
                Map.of("expiresAt", Instant.now().plus(365, ChronoUnit.DAYS).toString()), 200);

        assertThat(inbox()).isEmpty();
        assertThat(docs().get(0).get("state").asText()).isEqualTo("VALIDO");
        // E deixa de aparecer na lista do que está a caducar.
        assertThat(send(get("/api/v1/documents/expiring?withinDays=60"), null, 200)).isEmpty();
    }

    // ---- ficheiros e permissões -----------------------------------------
    @Test
    void uploadsAFileAndServesItThroughASignedUrl() throws Exception {
        setUpAsset("dc7@teste.ao", "CAM-007");

        MockMultipartFile ficheiro = new MockMultipartFile(
                "file", "apolice.pdf", "application/pdf",
                "%PDF-1.4 conteudo de teste".getBytes(StandardCharsets.UTF_8));

        MvcResult r = mvc.perform(multipart("/api/v1/assets/" + assetId + "/documents/upload")
                        .file(ficheiro)
                        .param("title", "Apólice AAA-123")
                        .param("kind", "INSURANCE")
                        .param("reference", "AP-2026-0099")
                        .param("issuer", "Seguradora Kwanza")
                        .param("expiresAt", Instant.now().plus(200, ChronoUnit.DAYS).toString())
                        .header("Authorization", bearer))
                .andExpect(status().isCreated()).andReturn();

        JsonNode doc = json.readTree(r.getResponse().getContentAsByteArray());
        assertThat(doc.get("fileName").asText()).isEqualTo("apolice.pdf");
        assertThat(doc.get("reference").asText()).isEqualTo("AP-2026-0099");
        String url = doc.get("fileUrl").asText();
        assertThat(url).contains("/api/v1/files/").contains("sig=");

        // O URL assinado serve o ficheiro sem sessão iniciada.
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void refusesUnsupportedFileFormats() throws Exception {
        setUpAsset("dc8@teste.ao", "CAM-008");
        MockMultipartFile executavel = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload",
                "MZ".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/assets/" + assetId + "/documents/upload")
                        .file(executavel)
                        .param("title", "Suspeito")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void techniciansRegisterButOnlyManagersRemove() throws Exception {
        setUpAsset("dc9@teste.ao", "CAM-009");
        String docId = documentDueIn("Livrete", "REGISTRATION", 300);

        Map<String, Object> convite = new HashMap<>();
        convite.put("email", "tecdoc@teste.ao");
        convite.put("role", "TECHNICIAN");
        String token = send(post("/api/v1/team/invitations"), convite, 201).get("token").asText();
        MvcResult r = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Zé", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn();

        String dono = bearer;
        bearer = "Bearer " + json.readTree(r.getResponse().getContentAsByteArray())
                .get("accessToken").asText();

        // O técnico regista e atualiza documentos no terreno...
        send(post("/api/v1/assets/" + assetId + "/documents"),
                Map.of("title", "Certificado de calibração", "kind", "CERTIFICATE"), 201);
        // ...mas apagar é decisão de quem gere.
        send(delete("/api/v1/documents/" + docId), null, 403);

        bearer = dono;
        send(delete("/api/v1/documents/" + docId), null, 200);
        assertThat(docs()).hasSize(1);
    }

    @Test
    void listsAvailableKindsAndRejectsUnknownOnes() throws Exception {
        setUpAsset("dc10@teste.ao", "CAM-010");
        JsonNode tipos = send(get("/api/v1/documents/kinds"), null, 200);
        assertThat(tipos.size()).isGreaterThanOrEqualTo(9);

        MockMultipartFile ficheiro = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/assets/" + assetId + "/documents/upload")
                        .file(ficheiro)
                        .param("title", "X")
                        .param("kind", "INVENTADO")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentsAreIsolatedBetweenCompanies() throws Exception {
        setUpAsset("dc11@teste.ao", "CAM-011");
        String docId = documentDueIn("Apólice", "INSURANCE", 100);

        bearer = register("dc11b@teste.ao", "Outra Empresa").bearer();
        send(patch("/api/v1/documents/" + docId), Map.of("title", "Roubado"), 404);
        send(delete("/api/v1/documents/" + docId), null, 404);
        assertThat(send(get("/api/v1/documents/expiring"), null, 200)).isEmpty();
    }
}
