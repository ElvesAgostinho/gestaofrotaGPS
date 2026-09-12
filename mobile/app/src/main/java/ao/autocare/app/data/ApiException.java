package ao.autocare.app.data;

/** Erro já pronto para mostrar ao utilizador (mensagem em português vinda da API). */
public class ApiException extends Exception {

    private final int statusCode;

    public ApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
