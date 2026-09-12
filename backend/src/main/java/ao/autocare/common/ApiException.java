package ao.autocare.common;

import org.springframework.http.HttpStatus;

/**
 * Exceção de negócio com mensagem já pronta para o utilizador (em português).
 * O {@link GlobalExceptionHandler} converte-a na resposta de erro.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /** 429: demasiadas tentativas no mesmo espaço de tempo. */
    public static ApiException tooManyRequests(String message) {
        return new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
