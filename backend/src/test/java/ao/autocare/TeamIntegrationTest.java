package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Gestão de equipa: convites, papéis e a matriz de permissões. */
class TeamIntegrationTest extends AbstractIntegrationTest {

    // ---- utilitários ------------------------------------------------
    private JsonNode send(MockHttpServletRequestBuilder req, String bearer, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] content = r.getResponse().getContentAsByteArray();
        return content.length == 0 ? json.nullNode() : json.readTree(content);
    }

    /** Convida alguém e devolve o token do convite (modo demonstração). */
    private JsonNode invite(String ownerBearer, String email, String role) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("name", "Convidado");
        body.put("role", role);
        return send(post("/api/v1/team/invitations"), ownerBearer, body, 201);
    }

    /** Aceita um convite criando conta nova e devolve o token de acesso. */
    private String acceptAsNewUser(String inviteToken, String name) throws Exception {
        JsonNode res = send(post("/api/v1/invitations/" + inviteToken + "/accept"), null,
                Map.of("name", name, "password", "palavraForte1"), 200);
        return "Bearer " + res.get("accessToken").asText();
    }

    private String newAssetType(String bearer, String name) throws Exception {
        return send(post("/api/v1/asset-types"), bearer, Map.of("name", name), 201)
                .get("id").asText();
    }

    // ---- testes -----------------------------------------------------
    @Test
    void ownerInvitesTechnicianWhoJoinsTheSameCompany() throws Exception {
        String owner = register("dono1@teste.ao", "Construções Kwanza").bearer();

        JsonNode invitation = invite(owner, "tecnico1@teste.ao", "TECHNICIAN");
        assertThat(invitation.get("demoMode").asBoolean()).isTrue();
        assertThat(invitation.get("acceptUrl").asText()).contains("/convite/");
        String token = invitation.get("token").asText();

        // A pessoa convidada vê a empresa antes de aceitar, sem sessão iniciada.
        mvc.perform(get("/api/v1/invitations/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Construções Kwanza"))
                .andExpect(jsonPath("$.email").value("tecnico1@teste.ao"))
                .andExpect(jsonPath("$.roleLabel").value("Técnico"))
                .andExpect(jsonPath("$.accountExists").value(false));

        String technician = acceptAsNewUser(token, "Ana Técnica");

        // Ficaram dois membros na mesma empresa.
        JsonNode members = send(get("/api/v1/team/members"), owner, null, 200);
        assertThat(members).hasSize(2);
        assertThat(members.get(1).get("role").asText()).isEqualTo("TECHNICIAN");
        assertThat(members.get(1).get("email").asText()).isEqualTo("tecnico1@teste.ao");

        // O técnico vê a mesma equipa — está mesmo dentro da empresa.
        assertThat(send(get("/api/v1/team/members"), technician, null, 200)).hasSize(2);

        // O convite deixa de estar pendente.
        JsonNode invites = send(get("/api/v1/team/invitations"), owner, null, 200);
        assertThat(invites.get(0).get("status").asText()).isEqualTo("ACEITE");
    }

    @Test
    void technicianWorksOnOrdersButCannotManageAssetsOrTeam() throws Exception {
        String owner = register("dono2@teste.ao").bearer();
        String typeId = newAssetType(owner, "Retroescavadora");
        String assetId = send(post("/api/v1/assets"), owner,
                Map.of("tag", "RE-001", "name", "Retro", "assetTypeId", typeId), 201)
                .get("id").asText();

        String technician = acceptAsNewUser(
                invite(owner, "tecnico2@teste.ao", "TECHNICIAN").get("token").asText(), "Zé Técnico");

        // Pode ler.
        send(get("/api/v1/assets/" + assetId), technician, null, 200);
        // Pode registar leituras e abrir ordens corretivas.
        send(post("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings"), technician,
                Map.of("value", 120), 201);
        send(post("/api/v1/work-orders"), technician,
                Map.of("assetId", assetId, "type", "CORRECTIVE", "title", "Fuga de óleo"), 201);

        // Não pode criar ativos nem mexer na equipa.
        JsonNode denied = send(post("/api/v1/assets"), technician,
                Map.of("tag", "RE-002", "name", "Outra", "assetTypeId", typeId), 403);
        // A mensagem diz a permissão que falta, pelo nome que o administrador
        // vê no ecrã de utilizadores — é lá que a vai dar.
        assertThat(denied.get("message").asText()).contains("equipamento").contains("administrador");
        send(post("/api/v1/team/invitations"), technician,
                Map.of("email", "x@teste.ao", "role", "VIEWER"), 403);
    }

    @Test
    void viewerCanOnlyRead() throws Exception {
        String owner = register("dono3@teste.ao").bearer();
        String typeId = newAssetType(owner, "Gerador");
        String assetId = send(post("/api/v1/assets"), owner,
                Map.of("tag", "GER-001", "name", "Gerador", "assetTypeId", typeId), 201)
                .get("id").asText();

        String viewer = acceptAsNewUser(
                invite(owner, "consulta@teste.ao", "VIEWER").get("token").asText(), "Rui Consulta");

        send(get("/api/v1/assets"), viewer, null, 200);
        send(get("/api/v1/dashboard"), viewer, null, 200);
        send(post("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings"), viewer,
                Map.of("value", 10), 403);
        send(post("/api/v1/work-orders"), viewer,
                Map.of("assetId", assetId, "type", "CORRECTIVE", "title", "X"), 403);
    }

    @Test
    void protectsTheCompanyFromLosingItsLastOwner() throws Exception {
        String owner = register("dono4@teste.ao").bearer();
        String ownMembership = send(get("/api/v1/team/members"), owner, null, 200)
                .get(0).get("id").asText();

        // Não se pode despromover, suspender nem remover a si próprio.
        assertThat(send(patch("/api/v1/team/members/" + ownMembership), owner,
                Map.of("role", "VIEWER"), 409).get("message").asText())
                .contains("próprio papel");
        send(patch("/api/v1/team/members/" + ownMembership), owner,
                Map.of("suspended", true), 409);
        send(delete("/api/v1/team/members/" + ownMembership), owner, null, 409);
    }

    @Test
    void ownerPromotesSuspendsAndRemovesAMember() throws Exception {
        String owner = register("dono5@teste.ao").bearer();
        String technician = acceptAsNewUser(
                invite(owner, "tecnico5@teste.ao", "TECHNICIAN").get("token").asText(), "Nuno");

        String membershipId = send(get("/api/v1/team/members"), owner, null, 200)
                .get(1).get("id").asText();

        // Promoção a gestor: passa a poder criar ativos.
        send(patch("/api/v1/team/members/" + membershipId), owner,
                Map.of("role", "MANAGER", "jobTitle", "Chefe de oficina"), 200);
        String typeId = newAssetType(technician, "Camião");
        send(post("/api/v1/assets"), technician,
                Map.of("tag", "CAM-001", "name", "Camião", "assetTypeId", typeId), 201);

        // Suspensão: perde o acesso à empresa de imediato.
        send(patch("/api/v1/team/members/" + membershipId), owner,
                Map.of("suspended", true), 200);
        send(get("/api/v1/team/members"), technician, null, 403);

        // Reativação e remoção.
        send(patch("/api/v1/team/members/" + membershipId), owner, Map.of("suspended", false), 200);
        send(get("/api/v1/team/members"), technician, null, 200);
        send(delete("/api/v1/team/members/" + membershipId), owner, null, 200);
        assertThat(send(get("/api/v1/team/members"), owner, null, 200)).hasSize(1);
    }

    @Test
    void rejectsDuplicateInvitesAndInvitingAnExistingMember() throws Exception {
        String owner = register("dono6@teste.ao").bearer();
        invite(owner, "repetido@teste.ao", "TECHNICIAN");

        // Segundo convite para o mesmo email enquanto o primeiro está pendente.
        assertThat(send(post("/api/v1/team/invitations"), owner,
                Map.of("email", "repetido@teste.ao", "role", "VIEWER"), 409)
                .get("message").asText()).contains("convite pendente");

        // Convidar alguém que já é membro.
        String other = register("dono6b@teste.ao").bearer();
        assertThat(other).isNotBlank();
        assertThat(send(post("/api/v1/team/invitations"), owner,
                Map.of("email", "dono6@teste.ao", "role", "VIEWER"), 409)
                .get("message").asText()).contains("já faz parte");
    }

    @Test
    void revokedAndUsedInvitationsCannotBeAccepted() throws Exception {
        String owner = register("dono7@teste.ao").bearer();

        JsonNode first = invite(owner, "anulado@teste.ao", "TECHNICIAN");
        send(delete("/api/v1/team/invitations/" + first.get("invitation").get("id").asText()),
                owner, null, 200);
        mvc.perform(get("/api/v1/invitations/" + first.get("token").asText()))
                .andExpect(status().isBadRequest());

        JsonNode second = invite(owner, "usado@teste.ao", "TECHNICIAN");
        String token = second.get("token").asText();
        acceptAsNewUser(token, "Já Entrou");
        send(post("/api/v1/invitations/" + token + "/accept"), null,
                Map.of("name", "Outra Vez", "password", "palavraForte1"), 409);
    }

    @Test
    void anExistingAccountAcceptsTheInviteFromItsOwnSession() throws Exception {
        String owner = register("dono8@teste.ao").bearer();
        String outsider = register("externo@teste.ao", "Empresa do Externo").bearer();

        String token = invite(owner, "externo@teste.ao", "MANAGER").get("token").asText();

        // Com conta já existente, o caminho público recusa e indica o correto.
        send(post("/api/v1/invitations/" + token + "/accept"), null,
                Map.of("name", "Externo", "password", "palavraForte1"), 409);

        JsonNode member = send(post("/api/v1/team/invitations/accept"), outsider,
                Map.of("token", token), 200);
        assertThat(member.get("role").asText()).isEqualTo("MANAGER");
        assertThat(send(get("/api/v1/team/members"), owner, null, 200)).hasSize(2);
    }

    @Test
    void ordersAreAssignedToRealTeamMembersAndFilteredByThem() throws Exception {
        String owner = register("dono10@teste.ao").bearer();
        String typeId = newAssetType(owner, "Escavadora");
        String assetId = send(post("/api/v1/assets"), owner,
                Map.of("tag", "ESC-001", "name", "Escavadora", "assetTypeId", typeId), 201)
                .get("id").asText();

        String technician = acceptAsNewUser(
                invite(owner, "tecnico10@teste.ao", "TECHNICIAN").get("token").asText(), "Paulo");
        JsonNode members = send(get("/api/v1/team/members"), owner, null, 200);
        String membershipId = members.get(1).get("id").asText();
        String technicianUserId = members.get(1).get("userId").asText();

        // Atribuir na criação: o nome mostrado vem do membro, não de texto livre.
        JsonNode wo = send(post("/api/v1/work-orders"), owner, Map.of(
                "assetId", assetId, "type", "PREVENTIVE", "title", "Revisão 500 h",
                "assignedToUserId", technicianUserId), 201);
        assertThat(wo.get("assignedToUserId").asText()).isEqualTo(technicianUserId);
        assertThat(wo.get("assignedToLabel").asText()).isEqualTo("Paulo");

        // "As minhas ordens" para o técnico.
        JsonNode mine = send(get("/api/v1/work-orders?assignedTo=me"), technician, null, 200);
        assertThat(mine.get("content")).hasSize(1);
        assertThat(mine.get("content").get(0).get("number").asText()).isEqualTo(
                wo.get("number").asText());
        // O dono não tem nada atribuído a si.
        assertThat(send(get("/api/v1/work-orders?assignedTo=me"), owner, null, 200)
                .get("content")).isEmpty();

        // Não se atribui a quem não é da equipa.
        String outsider = register("externo10@teste.ao", "Outra Empresa").userId();
        assertThat(send(post("/api/v1/work-orders"), owner, Map.of(
                "assetId", assetId, "type", "INSPECTION", "title", "X",
                "assignedToUserId", outsider), 400).get("message").asText())
                .contains("não faz parte da equipa");

        // Nem a um membro suspenso.
        send(patch("/api/v1/team/members/" + membershipId), owner, Map.of("suspended", true), 200);
        send(post("/api/v1/work-orders"), owner, Map.of(
                "assetId", assetId, "type", "INSPECTION", "title", "Y",
                "assignedToUserId", technicianUserId), 409);
    }

    @Test
    void listsTheRolesAvailableToTheCompany() throws Exception {
        String owner = register("dono9@teste.ao").bearer();
        JsonNode roles = send(get("/api/v1/team/roles"), owner, null, 200);
        assertThat(roles).hasSize(4);
        assertThat(roles.get(0).get("code").asText()).isEqualTo("OWNER");
        assertThat(roles.get(3).get("code").asText()).isEqualTo("VIEWER");
    }

    @Test
    void invitationEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/team/members")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/team/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.ao\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isUnauthorized());
    }
}
