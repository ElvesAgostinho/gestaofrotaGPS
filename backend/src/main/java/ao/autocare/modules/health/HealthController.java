package ao.autocare.modules.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sistema")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Operation(summary = "Estado do serviço")
    @GetMapping
    public Map<String, Object> check() {
        String database = "ok";
        try (var conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                database = "erro";
            }
        } catch (Exception e) {
            database = "erro";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok".equals(database) ? "ok" : "degradado");
        body.put("database", database);
        body.put("timestamp", Instant.now());
        return body;
    }
}
