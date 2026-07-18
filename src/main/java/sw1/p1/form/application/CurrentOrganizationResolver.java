package sw1.p1.form.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class CurrentOrganizationResolver {

    private final UserRepository userRepository;

    public String requireOrganizationId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario autenticado no encontrado"));
        String orgId = user.getOrganizationId();
        if (orgId == null || orgId.isBlank()) {
            throw new BusinessException("El usuario autenticado no pertenece a una organizacion");
        }
        return orgId;
    }

    public String requireEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
