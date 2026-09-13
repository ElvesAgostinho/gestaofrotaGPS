package ao.autocare;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTransactionalData() {
        // Limpa dados transacionais entre testes; mantém os dados de referência (V2).
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : new String[] {
                // Ordens de manutenção (V8)
                "repairs", "failures", "work_order_parts", "work_order_labor",
                "work_order_status_history", "work_order_quotes",
                "work_order_attachments", "work_order_services",
                "work_order_tasks", "work_orders", "org_counters",
                // Peças e stock (V7)
                "stock_movements", "stock_items", "warehouses", "parts", "budgets",
                // Planos de manutenção (V6)
                "plan_task_completions", "asset_plan_tasks", "asset_plans",
                "plan_task_parts", "plan_task_triggers", "plan_tasks", "maintenance_plans",
                // Checklists (V5)
                "checklist_execution_items", "checklist_executions",
                "checklist_items", "checklist_templates",
                // CMMS (V3) + ficheiros (V4)
                // Telemetria (V10)
                // Frota (V21), conducao (V22) e controlo de combustivel (V23)
                "fuel_card_transactions", "fuel_anomalies", "asset_consumption_baselines",
                "driver_scores", "driving_events",
                "route_waypoints", "routes", "driver_assignments", "driver_infractions", "driver_shifts", "drivers",
                "tyre_readings", "tyres",
                "device_commands", "fuel_records", "asset_documents",
                "predictive_readings", "predictive_programs",
                "telemetry_alerts",
                "geofence_events", "geofence_presence", "geofence_assets", "geofences",
                "gps_positions", "trips", "gps_devices",
                "asset_photos", "stored_files",
                "meter_readings", "asset_meters", "asset_criticality", "assets",
                "suppliers",
                "asset_systems", "asset_types", "locations",
                // Fase 1
                "audit_logs", "notification_preferences", "notifications", "payments",
                "subscriptions", "password_reset_tokens", "refresh_tokens", "verification_codes",
                "document_seals", "invitations", "memberships", "organizations", "users"
        }) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    /** Sessão de teste: token de acesso + ids úteis. */
    public record Session(String token, String userId, String organizationName) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Regista uma conta nova e devolve a sessão autenticada. */
    protected Session register(String email, String organizationName) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Utilizador Teste");
        payload.put("email", email);
        payload.put("password", "palavraForte1");
        payload.put("acceptTerms", true);
        if (organizationName != null) {
            payload.put("organizationName", organizationName);
        }
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        return new Session(
                body.get("accessToken").asText(),
                body.get("user").get("id").asText(),
                organizationName);
    }

    protected Session register(String email) throws Exception {
        return register(email, "Empresa Teste");
    }
}
