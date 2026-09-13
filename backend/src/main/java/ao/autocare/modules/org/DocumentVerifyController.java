package ao.autocare.modules.org;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Verificação pública de um documento pelo código do rodapé. Sem conta:
 * quem recebe o papel (fornecedor, auditor, autoridade) confirma que foi
 * emitido por esta empresa e, com o ficheiro, que não foi alterado.
 */
@Tag(name = "Verificação de documentos")
@RestController
public class DocumentVerifyController {

    private final DocumentSealService seals;

    public DocumentVerifyController(DocumentSealService seals) {
        this.seals = seals;
    }

    @Operation(summary = "Verificar um código de documento")
    @GetMapping("/api/v1/public/verify/{code}")
    public DocumentSealService.Verificacao verify(@PathVariable String code) {
        return seals.verificar(code, null);
    }

    @Operation(summary = "Verificar um código e o próprio ficheiro PDF (não foi alterado?)")
    @PostMapping(value = "/api/v1/public/verify/{code}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSealService.Verificacao verifyFile(@PathVariable String code,
            @RequestParam("file") MultipartFile file) throws IOException {
        return seals.verificar(code, file.getBytes());
    }
}
