package sw1.p1.form.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class CurrentOrganizationResolver {

    private final UserRepository userRepository;

    public String requireOrganizationId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new BusinessException("No existe un usuario autenticado");
        }
        var user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BusinessException("Usuario autenticado no encontrado"));
        String orgId = user.getOrganizationId();
        if (orgId == null || orgId.isBlank()) {
            throw new BusinessException("El usuario autenticado no pertenece a una organizacion");
        }
        return orgId;
    }

    public String requireEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new BusinessException("No existe un usuario autenticado");
        }
        return auth.getName();
    }
}
