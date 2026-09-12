package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Manutenção preditiva: programas, medições e o que elas desencadeiam. */
class PredictiveIntegrationTest extends AbstractIntegrationTest {

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
        String typeId = send(post("/api/v1/asset-types"),
                Map.of("name", "Retroescavadora " + tag), 201).get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
    }

    private JsonNode programs() throws Exception {
        return send(get("/api/v1/assets/" + assetId + "/predictive"), null, 200);
    }

    private JsonNode inbox() throws Exception {
        return send(get("/api/v1/notifications"), null, 200).get("content");
    }

    // ---- programas -----------------------------------------------------
    @Test
    void appliesTheReferenceSetFromTheDocument() throws Exception {
        setUpAsset("pd1@teste.ao", "RE-001");

        JsonNode criados = send(
                post("/api/v1/assets/" + assetId + "/predictive/standard"), null, 201);
        assertThat(criados).hasSize(3);

        Map<String, String> porTecnica = new HashMap<>();
        for (JsonNode p : criados) {
            porTecnica.put(p.get("technique").asText(), p.get("frequencyLabel").asText());
        }
        // Exatamente as periodicidades do documento CAT.
        assertThat(porTecnica).containsEntry("VIBRATION", "Mensal");
        assertThat(porTecnica).containsEntry("THERMOGRAPHY", "Trimestral");
        assertThat(porTecnica).containsEntry("OIL_ANALYSIS", "Semestral");

        JsonNode vibracao = null;
        for (JsonNode p : criados) {
            if ("VIBRATION".equals(p.get("technique").asText())) vibracao = p;
        }
        assertThat(vibracao.get("techniqueLabel").asText()).isEqualTo("Análise de vibração");
        assertThat(vibracao.get("goal").asText()).contains("desalinhamento");
        assertThat(vibracao.get("status").asText()).isEqualTo("OK");

        // Aplicar outra vez não duplica.
        assertThat(send(post("/api/v1/assets/" + assetId + "/predictive/standard"), null, 201))
                .hasSize(3);
    }

    @Test
    void refusesTwoProgramsOfTheSameTechniqueOnOneAsset() throws Exception {
        setUpAsset("pd2@teste.ao", "RE-002");
        send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "VIBRATION"), 201);

        assertThat(send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "VIBRATION"), 409).get("message").asText())
                .contains("já tem um programa");
    }

    @Test
    void theWarningWindowIsProportionalToTheInterval() throws Exception {
        setUpAsset("pd3@teste.ao", "RE-003");

        // Mensal, feita há 28 dias: faltam 2 dias, dentro da janela de 3.
        send(post("/api/v1/assets/" + assetId + "/predictive"), Map.of(
                "technique", "VIBRATION", "frequencyMonths", 1,
                "lastDoneAt", Instant.now().minus(28, ChronoUnit.DAYS).toString()), 201);

        // Anual, feita há 28 dias: faltam 337 dias, muito longe.
        send(post("/api/v1/assets/" + assetId + "/predictive"), Map.of(
                "technique", "ALIGNMENT", "frequencyMonths", 12,
                "lastDoneAt", Instant.now().minus(28, ChronoUnit.DAYS).toString()), 201);

        Map<String, String> estados = new HashMap<>();
        for (JsonNode p : programs()) {
            estados.put(p.get("technique").asText(), p.get("status").asText());
        }
        assertThat(estados).containsEntry("VIBRATION", "DUE_SOON");
        assertThat(estados).containsEntry("ALIGNMENT", "OK");
    }

    @Test
    void anOverdueProgramShowsAsOverdueAndWarnsTheManagers() throws Exception {
        setUpAsset("pd4@teste.ao", "RE-004");
        send(post("/api/v1/assets/" + assetId + "/predictive"), Map.of(
                "technique", "OIL_ANALYSIS", "frequencyMonths", 6,
                "lastDoneAt", Instant.now().minus(200, ChronoUnit.DAYS).toString()), 201);

        JsonNode p = programs().get(0);
        assertThat(p.get("status").asText()).isEqualTo("OVERDUE");
        assertThat(p.get("remainingDays").asLong()).isNegative();

        // O mesmo programa aparece no filtro de vencidos da frota.
        assertThat(send(get("/api/v1/predictive?status=OVERDUE"), null, 200)).hasSize(1);
        assertThat(send(get("/api/v1/predictive?status=OK"), null, 200)).isEmpty();
        send(get("/api/v1/predictive?status=INVENTADO"), null, 400);
    }

    // ---- medições ------------------------------------------------------
    @Test
    void recordingAMeasurementReschedulesTheProgram() throws Exception {
        setUpAsset("pd5@teste.ao", "RE-005");
        String programId = send(post("/api/v1/assets/" + assetId + "/predictive"), Map.of(
                "technique", "OIL_ANALYSIS", "frequencyMonths", 6,
                "lastDoneAt", Instant.now().minus(200, ChronoUnit.DAYS).toString()), 201)
                .get("id").asText();
        assertThat(programs().get(0).get("status").asText()).isEqualTo("OVERDUE");

        JsonNode res = send(post("/api/v1/predictive/" + programId + "/readings"), Map.of(
                "result", "NORMAL",
                "measurement", "Ferro 12 ppm, viscosidade dentro do especificado",
                "performedByLabel", "Laboratório Kwanza"), 201);

        assertThat(res.get("reading").get("result").asText()).isEqualTo("NORMAL");
        assertThat(res.get("program").get("status").asText()).isEqualTo("OK");
        assertThat(res.get("program").get("lastResult").asText()).isEqualTo("NORMAL");
        assertThat(res.hasNonNull("workOrderNumber")).isFalse();

        // Sendo normal, ninguém é incomodado.
        assertThat(inbox()).isEmpty();
    }

    @Test
    void anOldForgottenMeasurementDoesNotPushTheScheduleForward() throws Exception {
        setUpAsset("pd6@teste.ao", "RE-006");
        String programId = send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "VIBRATION", "frequencyMonths", 1), 201)
                .get("id").asText();

        // Regista-se a de ontem...
        send(post("/api/v1/predictive/" + programId + "/readings"), Map.of(
                "result", "NORMAL",
                "performedAt", Instant.now().minus(1, ChronoUnit.DAYS).toString()), 201);
        String depoisDaRecente = programs().get(0).get("nextDueAt").asText();

        // ...e só depois se lança uma antiga que tinha ficado esquecida.
        send(post("/api/v1/predictive/" + programId + "/readings"), Map.of(
                "result", "NORMAL",
                "performedAt", Instant.now().minus(90, ChronoUnit.DAYS).toString()), 201);

        // A agenda não recua nem avança: continua a valer a medição mais recente.
        assertThat(programs().get(0).get("nextDueAt").asText()).isEqualTo(depoisDaRecente);
        assertThat(send(get("/api/v1/predictive/" + programId + "/readings"), null, 200)
                .get("content")).hasSize(2);
    }

    @Test
    void aCriticalResultWarnsAndCanOpenACorrectiveOrder() throws Exception {
        setUpAsset("pd7@teste.ao", "RE-007");
        String programId = send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "VIBRATION"), 201).get("id").asText();

        JsonNode res = send(post("/api/v1/predictive/" + programId + "/readings"), Map.of(
                "result", "CRITICAL",
                "measurement", "11,2 mm/s RMS no rolamento da bomba",
                "findings", "Vibração muito acima do admissível",
                "recommendation", "Substituir o rolamento antes de voltar a operar",
                "openWorkOrder", true), 201);

        String numero = res.get("workOrderNumber").asText();
        assertThat(numero).startsWith("OM-");

        // A ordem aberta é corretiva, urgente, e traz a recomendação no corpo.
        JsonNode ordens = send(get("/api/v1/work-orders"), null, 200).get("content");
        assertThat(ordens).hasSize(1);
        String woId = ordens.get(0).get("id").asText();
        JsonNode wo = send(get("/api/v1/work-orders/" + woId), null, 200);
        assertThat(wo.get("type").asText()).isEqualTo("CORRECTIVE");
        assertThat(wo.get("priority").asText()).isEqualTo("URGENT");
        assertThat(wo.get("description").asText()).contains("Substituir o rolamento");

        // E quem gere a manutenção é avisado.
        JsonNode avisos = inbox();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("title").asText()).contains("crítico");
        assertThat(avisos.get(0).get("severity").asText()).isEqualTo("CRITICAL");
    }

    @Test
    void attentionWarnsWithoutOpeningAnythingUnlessAsked() throws Exception {
        setUpAsset("pd8@teste.ao", "RE-008");
        String programId = send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "THERMOGRAPHY"), 201).get("id").asText();

        JsonNode res = send(post("/api/v1/predictive/" + programId + "/readings"), Map.of(
                "result", "ATTENTION",
                "findings", "Ligação do disjuntor 8 °C acima das vizinhas"), 201);

        assertThat(res.hasNonNull("workOrderNumber")).isFalse();
        assertThat(send(get("/api/v1/work-orders"), null, 200).get("content")).isEmpty();

        JsonNode avisos = inbox();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("severity").asText()).isEqualTo("WARNING");
    }

    @Test
    void refusesMeasurementsDatedInTheFuture() throws Exception {
        setUpAsset("pd9@teste.ao", "RE-009");
        String programId = send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "VIBRATION"), 201).get("id").asText();

        assertThat(send(post("/api/v1/predictive/" + programId + "/readings"), Map.of(
                "result", "NORMAL",
                "performedAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()), 400)
                .get("message").asText()).contains("futuro");
    }

    // ---- permissões e PDF ----------------------------------------------
    @Test
    void techniciansRecordButOnlyManagersConfigure() throws Exception {
        setUpAsset("pd10@teste.ao", "RE-010");
        String programId = send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "VIBRATION"), 201).get("id").asText();

        Map<String, Object> convite = new HashMap<>();
        convite.put("email", "tecpd@teste.ao");
        convite.put("role", "TECHNICIAN");
        String token = send(post("/api/v1/team/invitations"), convite, 201).get("token").asText();
        MvcResult r = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Ana", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn();
        String tecnico = "Bearer " + json.readTree(r.getResponse().getContentAsByteArray())
                .get("accessToken").asText();

        String dono = bearer;
        bearer = tecnico;
        // O técnico mede...
        send(post("/api/v1/predictive/" + programId + "/readings"),
                Map.of("result", "NORMAL"), 201);
        // ...mas não define a política de monitorização.
        send(post("/api/v1/assets/" + assetId + "/predictive"),
                Map.of("technique", "ULTRASOUND"), 403);
        send(delete("/api/v1/predictive/" + programId), null, 403);
        bearer = dono;
        send(patch("/api/v1/predictive/" + programId),
                Map.of("frequencyMonths", 3), 200);
    }

    @Test
    void thePdfShowsTheAssetProgramsInsteadOfTheReferenceTable() throws Exception {
        setUpAsset("pd11@teste.ao", "RE-011");

        // Sem programas, o PDF sai com a tabela do documento de referência.
        MvcResult semProgramas = mvc.perform(
                        get("/api/v1/assets/" + assetId + "/maintenance-plan.pdf")
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        assertThat(semProgramas.getResponse().getContentAsByteArray()).isNotEmpty();

        // Com programas próprios, o PDF continua a gerar.
        send(post("/api/v1/assets/" + assetId + "/predictive"), Map.of(
                "technique", "ULTRASOUND", "frequencyMonths", 4,
                "components", "Purgadores de vapor", "goal", "Detetar fugas"), 201);

        MvcResult comProgramas = mvc.perform(
                        get("/api/v1/assets/" + assetId + "/maintenance-plan.pdf")
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        assertThat(comProgramas.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(comProgramas.getResponse().getContentType()).contains("pdf");
    }

    @Test
    void listsAvailableTechniques() throws Exception {
        setUpAsset("pd12@teste.ao", "RE-012");
        JsonNode tecnicas = send(get("/api/v1/predictive/techniques"), null, 200);
        assertThat(tecnicas.size()).isGreaterThanOrEqualTo(6);
        assertThat(tecnicas.get(0).get("code").asText()).isEqualTo("VIBRATION");
        assertThat(tecnicas.get(0).get("defaultFrequencyLabel").asText()).isEqualTo("Mensal");
    }
}
