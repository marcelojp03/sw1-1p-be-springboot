package sw1.p1.form.exception;

public class FormVersionNotFoundException extends RuntimeException {
    public FormVersionNotFoundException(String message) {
        super(message);
    }
}
