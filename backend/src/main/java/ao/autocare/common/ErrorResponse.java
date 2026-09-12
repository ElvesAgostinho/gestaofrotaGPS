package ao.autocare.common;

import java.time.Instant;
import java.util.List;

/**
 * Corpo de erro uniforme. Nunca contém detalhe técnico (regra de UX #65):
 * a mensagem é sempre compreensível e em português.
 */
public record ErrorResponse(
        int statusCode,
        String message,
        List<FieldError> errors,
        String path,
        Instant timestamp) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(int statusCode, String message, String path) {
        return new ErrorResponse(statusCode, message, null, path, Instant.now());
    }

    public static ErrorResponse of(
            int statusCode, String message, List<FieldError> errors, String path) {
        return new ErrorResponse(statusCode, message, errors, path, Instant.now());
    }
}
