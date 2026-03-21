package tech.nabor.api.error;

public class NaborException extends RuntimeException {
    public enum Kind { DB_ERROR, HTTP_ERROR, AUTH_ERROR, NOT_FOUND, VALIDATION }

    private final Kind kind;

    public NaborException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}