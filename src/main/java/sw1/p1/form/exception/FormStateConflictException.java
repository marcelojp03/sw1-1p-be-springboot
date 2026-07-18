package sw1.p1.form.exception;

public class FormStateConflictException extends RuntimeException {
    public FormStateConflictException(String message) {
        super(message);
    }
}
