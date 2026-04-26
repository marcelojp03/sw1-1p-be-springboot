package sw1.p1.client.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.client.application.ClientService;
import sw1.p1.client.dto.ClientResponse;
import sw1.p1.client.dto.CreateClientRequest;
import sw1.p1.client.dto.UpdateClientRequest;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> findByOrganization(
            @RequestParam String organizationId) {
        return ResponseEntity.ok(clientService.findByOrganization(organizationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    /** Buscar cliente por número de documento para iniciar trámite */
    @GetMapping("/search")
    public ResponseEntity<ClientResponse> search(
            @RequestParam String documentNumber,
            @RequestParam String organizationId) {
        return ResponseEntity.ok(clientService.findByDocumentNumber(documentNumber, organizationId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.update(id, request));
    }
}
