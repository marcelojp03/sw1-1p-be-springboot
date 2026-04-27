package sw1.p1.organization.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.organization.domain.Area;
import sw1.p1.organization.domain.Organization;
import sw1.p1.organization.domain.OrganizationRepository;
import sw1.p1.organization.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationResponse create(CreateOrganizationRequest request) {
        if (organizationRepository.existsByName(request.name())) {
            throw new ConflictException("Ya existe una organización con ese nombre");
        }

        Organization org = Organization.builder()
                .name(request.name())
                .businessType(request.businessType())
                .ruc(request.ruc())
                .logoUrl(request.logoUrl())
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toResponse(organizationRepository.save(org));
    }

    public List<OrganizationResponse> findAll() {
        return organizationRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public OrganizationResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    public OrganizationResponse addArea(String orgId, CreateAreaRequest request) {
        Organization org = getOrThrow(orgId);

        Area area = Area.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .build();

        org.getAreas().add(area);
        org.setUpdatedAt(Instant.now());

        return toResponse(organizationRepository.save(org));
    }

    public OrganizationResponse update(String id, UpdateOrganizationRequest request) {
        Organization org = getOrThrow(id);

        if (request.name() != null && !request.name().isBlank()) {
            if (!request.name().equals(org.getName()) && organizationRepository.existsByName(request.name())) {
                throw new ConflictException("Ya existe una organización con ese nombre");
            }
            org.setName(request.name());
        }
        if (request.businessType() != null) org.setBusinessType(request.businessType());
        if (request.ruc() != null) org.setRuc(request.ruc());
        if (request.logoUrl() != null) org.setLogoUrl(request.logoUrl());
        if (request.active() != null) org.setActive(request.active());

        org.setUpdatedAt(Instant.now());
        return toResponse(organizationRepository.save(org));
    }

    public OrganizationResponse removeArea(String orgId, String areaId) {
        Organization org = getOrThrow(orgId);
        org.getAreas().removeIf(a -> a.getId().equals(areaId));
        org.setUpdatedAt(Instant.now());
        return toResponse(organizationRepository.save(org));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Organization getOrThrow(String id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada: " + id));
    }

    private OrganizationResponse toResponse(Organization org) {
        List<AreaResponse> areas = org.getAreas().stream()
                .map(a -> new AreaResponse(a.getId(), a.getName(), a.getDescription()))
                .collect(Collectors.toList());
        return new OrganizationResponse(org.getId(), org.getName(), org.getBusinessType(),
                org.getRuc(), org.getLogoUrl(), org.isActive(), areas);
    }
}
