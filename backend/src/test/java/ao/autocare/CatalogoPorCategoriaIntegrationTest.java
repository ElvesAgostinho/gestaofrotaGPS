package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.plan.PlanCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * As quatro famílias tratadas em separado: máquina, camião, ligeiro e gerador.
 *
 * <p>É onde se prova que o sistema não trata uma frota como se fosse toda
 * igual: cada família tem o seu plano por sistema e intervalo, a sua inspeção
 * diária e as suas técnicas preditivas — porque medir vibração no quadro de um
 * gerador não diz nada a ninguém, e um Hilux não tem quinta roda para lubrificar.
 */
class CatalogoPorCategoriaIntegrationTest extends AbstractIntegrationTest {

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

    private String ativo(String tag, String tipo, String categoria, String medidor) throws Exception {
        String tipoId = send(post("/api/v1/asset-types"),
                Map.of("name", tipo, "category", categoria, "primaryMeter", medidor), 201)
                .get("id").asText();
        return send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", tipo, "assetTypeId", tipoId), 201).get("id").asText();
    }

    // ==== A regra da família ================================================

    @Test
    void aFamiliaSaiDaCategoriaEDoNomeDoTipo() {
        assertThat(PlanCatalog.codigoPara("MACHINE", "Retroescavadora")).isEqualTo("RETROESCAVADORA");
        assertThat(PlanCatalog.codigoPara("GENERATOR", "Gerador Diesel")).isEqualTo("GENERATOR");
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Camião basculante")).isEqualTo("TRUCK_HEAVY");
        // Uma viatura ligeira não leva o plano de um pesado só porque é «viatura».
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Ligeiro de passageiros")).isEqualTo("LIGHT_VEHICLE");
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Pick-up 4x4")).isEqualTo("LIGHT_VEHICLE");
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Carrinha de caixa aberta")).isEqualTo("LIGHT_VEHICLE");
        // Um gerador continua a ser um gerador mesmo com a categoria mal posta.
        assertThat(PlanCatalog.codigoPara("MACHINE", "Grupo electrogéneo")).isEqualTo("GENERATOR");
    }

    // ==== O plano de cada família ===========================================

    @Test
    void cadaFamiliaTemAMatrizPorSistemaEIntervalo() {
        // A máquina: os oito sistemas do documento, às 250, 500, 1000 e 2000 horas.
        var maquina = PlanCatalog.build("RETROESCAVADORA", null).tasks();
        assertThat(maquina).hasSizeGreaterThan(30);
        for (String sistema : new String[] {"MOTOR", "HIDRAULICO", "COMBUSTIVEL", "TRANSMISSAO",
                "EIXOS", "ELETRICO", "TRAVAGEM", "ESTRUTURA"}) {
            assertThat(maquina).as("máquina, sistema " + sistema)
                    .anyMatch(t -> sistema.equals(t.systemCode()));
        }
        for (int horas : new int[] {250, 500, 1000, 2000}) {
            assertThat(maquina).as("máquina, " + horas + " h")
                    .anyMatch(t -> t.triggers().stream().anyMatch(
                            g -> g.interval() != null && g.interval().intValue() == horas));
        }

        // O camião: a mesma ideia, mas o contador é o odómetro.
        var camiao = PlanCatalog.build("TRUCK_HEAVY", null).tasks();
        assertThat(camiao).hasSizeGreaterThan(15);
        for (String sistema : new String[] {"MOTOR", "COMBUSTIVEL", "TRANSMISSAO", "EIXOS",
                "ELETRICO", "TRAVAGEM", "SUSPENSAO", "RODADO", "ESTRUTURA"}) {
            assertThat(camiao).as("camião, sistema " + sistema)
                    .anyMatch(t -> sistema.equals(t.systemCode()));
        }
        for (int km : new int[] {10_000, 20_000, 40_000, 80_000}) {
            assertThat(camiao).as("camião, " + km + " km")
                    .anyMatch(t -> t.triggers().stream().anyMatch(
                            g -> g.interval() != null && g.interval().intValue() == km));
        }

        // O ligeiro: por quilómetros, e o que não segue o odómetro vai por tempo.
        var ligeiro = PlanCatalog.build("LIGHT_VEHICLE", null).tasks();
        assertThat(ligeiro).hasSizeGreaterThan(12);
        assertThat(ligeiro).as("ligeiro não tem lubrificação de pinos")
                .noneMatch(t -> "LUBRIFICACAO".equals(t.systemCode()));
        assertThat(ligeiro).as("ligeiro tem climatização")
                .anyMatch(t -> "CLIMATIZACAO".equals(t.systemCode()));
        assertThat(ligeiro).as("o líquido de travões vai por tempo, não por km")
                .anyMatch(t -> t.triggers().stream().anyMatch(
                        g -> g.type() == ao.autocare.domain.enums.Enums.PlanTriggerType.CALENDAR_DAYS
                                && g.interval() != null && g.interval().intValue() == 730));

        // O gerador: ensaio semanal em carga e a matriz por horas.
        var gerador = PlanCatalog.build("GENERATOR", null).tasks();
        assertThat(gerador).hasSizeGreaterThan(12);
        for (String sistema : new String[] {"ENSAIO", "MOTOR", "COMBUSTIVEL", "ARREFECIMENTO",
                "ELETRICO", "ALTERNADOR", "QUADRO", "ESTRUTURA"}) {
            assertThat(gerador).as("gerador, sistema " + sistema)
                    .anyMatch(t -> sistema.equals(t.systemCode()));
        }
        for (int horas : new int[] {250, 500, 1000, 2000}) {
            assertThat(gerador).as("gerador, " + horas + " h")
                    .anyMatch(t -> t.triggers().stream().anyMatch(
                            g -> g.interval() != null && g.interval().intValue() == horas));
        }
    }

    // ==== A inspeção diária de cada família =================================

    @Test
    void aInspecaoDiariaEDiferenteEmCadaFamilia() throws Exception {
        bearer = register("cat1@teste.ao", "Frota Mista").bearer();

        String hilux = ativo("LIG-1", "Ligeiro 4x4 pick-up", "VEHICLE", "ODOMETER");
        JsonNode fichaLigeiro = send(get("/api/v1/assets/" + hilux + "/daily-inspection"), null, 200);
        assertThat(fichaLigeiro.get("name").asText()).contains("ligeiro");
        assertThat(fichaLigeiro.toString()).contains("Triângulo");
        assertThat(fichaLigeiro.toString()).doesNotContain("Fixação da carga");

        String camiao = ativo("CAM-1", "Camião basculante", "VEHICLE", "ODOMETER");
        JsonNode fichaCamiao = send(get("/api/v1/assets/" + camiao + "/daily-inspection"), null, 200);
        assertThat(fichaCamiao.get("name").asText()).contains("camião");
        assertThat(fichaCamiao.toString()).contains("Fixação da carga");

        String gerador = ativo("GER-1", "Gerador Diesel", "GENERATOR", "HOURMETER");
        JsonNode fichaGerador = send(get("/api/v1/assets/" + gerador + "/daily-inspection"), null, 200);
        assertThat(fichaGerador.get("name").asText()).contains("gerador");
        assertThat(fichaGerador.toString()).contains("transferência");
    }

    // ==== Os programas preditivos de cada família ===========================

    @Test
    void oConjuntoDeReferenciaPreditivoRespeitaAFamilia() throws Exception {
        bearer = register("cat2@teste.ao", "Frota Preditiva").bearer();

        String gerador = ativo("GER-2", "Gerador Diesel", "GENERATOR", "HOURMETER");
        JsonNode programasGerador =
                send(post("/api/v1/assets/" + gerador + "/predictive/standard"), null, 201);
        assertThat(programasGerador.toString()).contains("INSULATION");
        assertThat(programasGerador.toString()).doesNotContain("VIBRATION");

        String camiao = ativo("CAM-2", "Camião basculante", "VEHICLE", "ODOMETER");
        JsonNode programasCamiao =
                send(post("/api/v1/assets/" + camiao + "/predictive/standard"), null, 201);
        assertThat(programasCamiao.toString()).contains("OIL_ANALYSIS");
        assertThat(programasCamiao.toString()).contains("ALIGNMENT");

        String maquina = ativo("RE-2", "Retroescavadora", "MACHINE", "HOURMETER");
        JsonNode programasMaquina =
                send(post("/api/v1/assets/" + maquina + "/predictive/standard"), null, 201);
        assertThat(programasMaquina.toString()).contains("VIBRATION");
        assertThat(programasMaquina.toString()).contains("THERMOGRAPHY");
        assertThat(programasMaquina.toString()).contains("OIL_ANALYSIS");
    }
}
