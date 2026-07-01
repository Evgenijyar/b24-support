package ru.abs7.b24support.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.api.dto.PortalInstallationListResponse;
import ru.abs7.b24support.api.dto.PortalInstallationRequest;
import ru.abs7.b24support.api.dto.PortalInstallationResponse;
import ru.abs7.b24support.service.PortalInstallationService;

@RestController
@RequestMapping("/api/portals")
public class PortalController {

    private final PortalInstallationService portalInstallationService;

    public PortalController(PortalInstallationService portalInstallationService) {
        this.portalInstallationService = portalInstallationService;
    }

    @GetMapping
    public PortalInstallationListResponse list() {
        return portalInstallationService.list();
    }

    @GetMapping("/{id}")
    public PortalInstallationResponse get(@PathVariable Long id) {
        return portalInstallationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortalInstallationResponse create(@Valid @RequestBody PortalInstallationRequest request) {
        return portalInstallationService.create(request);
    }

    @PutMapping("/{id}")
    public PortalInstallationResponse update(@PathVariable Long id,
                                             @Valid @RequestBody PortalInstallationRequest request) {
        return portalInstallationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        portalInstallationService.delete(id);
    }
}
