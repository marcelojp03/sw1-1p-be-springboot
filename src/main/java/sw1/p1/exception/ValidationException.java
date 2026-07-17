package sw1.p1.exception;

import sw1.p1.policy.application.BpmnValidationService.Violation;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<Violation> violations;

    public ValidationException(List<Violation> violations) {
        super("La política no puede publicarse: " + violations.size() + " error(es)");
        this.violations = violations;
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
