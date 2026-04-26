package sw1.p1.client.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.Client;
import sw1.p1.client.domain.ClientRepository;
import sw1.p1.client.dto.ClientResponse;
import sw1.p1.client.dto.CreateClientRequest;
import sw1.p1.client.dto.UpdateClientRequest;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public ClientResponse create(CreateClientRequest request) {
        if (clientRepository.existsByDocumentNumberAndOrganizationId(
                request.documentNumber(), request.organizationId())) {
            throw new ConflictException("Ya existe un cliente con ese número de documento en la organización");
        }

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        String createdBy = userRepository.findByEmail(currentUsername)
                .map(u -> u.getId())
                .orElse(null);

        Client client = Client.builder()
                .organizationId(request.organizationId())
                .fullName(request.fullName())
                .documentType(request.documentType())
                .documentNumber(request.documentNumber())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toResponse(clientRepository.save(client));
    }

    public List<ClientResponse> findByOrganization(String organizationId) {
        return clientRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ClientResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    public ClientResponse findByDocumentNumber(String documentNumber, String organizationId) {
        return clientRepository.findByDocumentNumberAndOrganizationId(documentNumber, organizationId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException(
                        "Cliente no encontrado con documento: " + documentNumber));
    }

    public ClientResponse update(String id, UpdateClientRequest request) {
        Client client = getOrThrow(id);
        client.setFullName(request.fullName());
        client.setPhone(request.phone());
        client.setEmail(request.email());
        client.setAddress(request.address());
        client.setUpdatedAt(Instant.now());
        return toResponse(clientRepository.save(client));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Client getOrThrow(String id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado: " + id));
    }

    private ClientResponse toResponse(Client c) {
        return new ClientResponse(
                c.getId(),
                c.getOrganizationId(),
                c.getFullName(),
                c.getDocumentType(),
                c.getDocumentNumber(),
                c.getPhone(),
                c.getEmail(),
                c.getAddress(),
                c.getUserId(),
                c.getCreatedBy(),
                c.getCreatedAt()
        );
    }
}
