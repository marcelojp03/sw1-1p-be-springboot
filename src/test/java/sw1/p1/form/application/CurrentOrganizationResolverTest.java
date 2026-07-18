package sw1.p1.form.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.BusinessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CurrentOrganizationResolverTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityContext securityContext = mock(SecurityContext.class);
    private final Authentication authentication = mock(Authentication.class);

    private CurrentOrganizationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentOrganizationResolver(userRepository);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @Test
    void resolvesOrganizationId() {
        try (var holder = mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("admin@demo.com");
            var user = mock(User.class);
            when(user.getOrganizationId()).thenReturn("org-1");
            when(userRepository.findByEmail("admin@demo.com")).thenReturn(Optional.of(user));

            assertThat(resolver.requireOrganizationId()).isEqualTo("org-1");
        }
    }

    @Test
    void nullAuthenticationThrows() {
        try (var holder = mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(null);

            assertThatThrownBy(() -> resolver.requireOrganizationId())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No existe un usuario autenticado");
        }
    }

    @Test
    void nullOrganizationIdThrows() {
        try (var holder = mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("admin@demo.com");
            var user = mock(User.class);
            when(user.getOrganizationId()).thenReturn(null);
            when(userRepository.findByEmail("admin@demo.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> resolver.requireOrganizationId())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("organizacion");
        }
    }

    @Test
    void emptyOrganizationIdThrows() {
        try (var holder = mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("admin@demo.com");
            var user = mock(User.class);
            when(user.getOrganizationId()).thenReturn("");
            when(userRepository.findByEmail("admin@demo.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> resolver.requireOrganizationId())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("organizacion");
        }
    }

    @Test
    void userNotFoundThrows() {
        try (var holder = mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("nobody@demo.com");
            when(userRepository.findByEmail("nobody@demo.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resolver.requireOrganizationId())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Usuario autenticado no encontrado");
        }
    }
}
