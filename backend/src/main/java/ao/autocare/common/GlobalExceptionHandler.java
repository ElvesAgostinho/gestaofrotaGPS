package ao.autocare.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz qualquer exceção numa {@link ErrorResponse} amigável.
 * O detalhe técnico fica apenas nos logs do servidor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** A frase de um registo alterado por outra pessoa. Diz o que fazer. */
    public static final String MENSAGEM_VERSAO =
            "Este registo foi alterado por outra pessoa enquanto o editava. "
                    + "Recarregue a ficha e volte a aplicar as suas alterações.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        log.warn("{} {} -> {}: {}", req.getMethod(), req.getRequestURI(), ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getStatus().value(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        400,
                        "Alguns campos não foram preenchidos corretamente.",
                        fields,
                        req.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "O pedido não foi entendido. Verifique os dados enviados.",
                        req.getRequestURI()));
    }

    /**
     * Duas pessoas gravaram o mesmo registo; a segunda perde -- e fica a saber.
     *
     * <p>A alternativa, deixar a segunda gravacao passar por cima da primeira,
     * e a que os sistemas «simples» escolhem por omissao. E a que faz um gestor
     * perder meia hora de trabalho sem nunca perceber porque.
     */
    @ExceptionHandler({
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        jakarta.persistence.OptimisticLockException.class,
        org.hibernate.StaleObjectStateException.class
    })
    public ResponseEntity<ErrorResponse> handleStale(Exception e, HttpServletRequest req) {
        log.warn("{} {} -> 409 registo alterado entretanto", req.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, MENSAGEM_VERSAO, req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {} {}: {}", req.getMethod(), req.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Já existe um registo com estes dados.", req.getRequestURI()));
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Não tem permissão para aceder a esta área.",
                        req.getRequestURI()));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Não encontrámos o que procura.", req.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(405, "Operação não permitida neste recurso.", req.getRequestURI()));
    }

    @ExceptionHandler({
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ErrorResponse> handleMediaType(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(415, "Formato de pedido não suportado.", req.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Falta um parâmetro obrigatório: " + ex.getParameterName() + ".",
                        req.getRequestURI()));
    }

    /**
     * Falta uma parte do formulario (tipicamente o proprio ficheiro).
     *
     * <p>Sem isto caia no apanha-tudo e o utilizador via "erro inesperado" —
     * um 500 — quando o pedido e que estava mal feito.
     */
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            org.springframework.web.multipart.support.MissingServletRequestPartException ex,
            HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Nenhum ficheiro foi enviado.", req.getRequestURI()));
    }

    /**
     * Valor que nao encaixa no tipo esperado — um estado, uma data ou um
     * numero escritos de outra maneira.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
            HttpServletRequest req) {
        String esperado = "";
        Class<?> tipo = ex.getRequiredType();
        if (tipo != null && tipo.isEnum()) {
            esperado = " Valores aceites: " + String.join(", ",
                    java.util.Arrays.stream(tipo.getEnumConstants())
                            .map(String::valueOf).toList()) + ".";
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400,
                        "Valor inválido para \"" + ex.getName() + "\"." + esperado,
                        req.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(
            org.springframework.web.multipart.MultipartException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400,
                        "O ficheiro é demasiado grande ou está danificado. O limite é 15 MB por ficheiro.",
                        req.getRequestURI()));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponse(
            ErrorResponseException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = switch (status) {
            case NOT_FOUND -> "Não encontrámos o que procura.";
            case METHOD_NOT_ALLOWED -> "Operação não permitida neste recurso.";
            case UNSUPPORTED_MEDIA_TYPE -> "Formato de pedido não suportado.";
            default -> "Não foi possível processar o pedido.";
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), message, req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500,
                        "Ocorreu um erro inesperado. Tente novamente dentro de instantes.",
                        req.getRequestURI()));
    }

    private ErrorResponse.FieldError toFieldError(FieldError fe) {
        return new ErrorResponse.FieldError(
                fe.getField(),
                fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor inválido.");
    }
}
