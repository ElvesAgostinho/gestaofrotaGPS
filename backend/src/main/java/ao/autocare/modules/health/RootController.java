package ao.autocare.modules.health;

import ao.autocare.config.AutoCareProperties;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raiz da API. Quem abrir o endereço da API num navegador é mandado para a
 * aplicação web; a API em si não tem página.
 */
@Hidden
@RestController
public class RootController {

    private final AutoCareProperties props;

    public RootController(AutoCareProperties props) {
        this.props = props;
    }

    @GetMapping({"/", "/index.html"})
    public ResponseEntity<Void> raiz() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, props.app().webUrl())
                .build();
    }
}
