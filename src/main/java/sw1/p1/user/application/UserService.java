package sw1.p1.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.auth.dto.UserResponse;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.user.dto.UpdateUserRequest;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    public UserResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    public UserResponse update(String id, UpdateUserRequest request) {
        User user = getOrThrow(id);

        // Validar email único si cambió
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("El email ya está en uso por otro usuario");
        }

        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPositionName(request.positionName());
        user.setRoles(request.roles());
        user.setOrganizationId(request.organizationId());
        user.setAreaId(request.areaId());

        return toResponse(userRepository.save(user));
    }

    public UserResponse setActive(String id, boolean active) {
        User user = getOrThrow(id);
        user.setActive(active);
        return toResponse(userRepository.save(user));
    }

    public void delete(String id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Usuario no encontrado: " + id);
        }
        userRepository.deleteById(id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User getOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles(),
                user.getPhone(),
                user.getPositionName(),
                user.getOrganizationId(),
                user.getAreaId(),
                user.getClientId(),
                user.isActive()
        );
    }
}
