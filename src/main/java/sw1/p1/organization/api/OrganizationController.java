package sw1.p1.organization.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.organization.application.OrganizationService;
import sw1.p1.organization.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> findAll() {
        return ResponseEntity.ok(organizationService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(organizationService.findById(id));
    }

    @PostMapping("/{id}/areas")
    public ResponseEntity<OrganizationResponse> addArea(
            @PathVariable String id,
            @Valid @RequestBody CreateAreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.addArea(id, request));
    }

    @DeleteMapping("/{id}/areas/{areaId}")
    public ResponseEntity<OrganizationResponse> removeArea(
            @PathVariable String id,
            @PathVariable String areaId) {
        return ResponseEntity.ok(organizationService.removeArea(id, areaId));
    }
}
