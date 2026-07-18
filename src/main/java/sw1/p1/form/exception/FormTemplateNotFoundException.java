package sw1.p1.form.exception;

public class FormTemplateNotFoundException extends RuntimeException {
    public FormTemplateNotFoundException(String message) {
        super(message);
    }
}
