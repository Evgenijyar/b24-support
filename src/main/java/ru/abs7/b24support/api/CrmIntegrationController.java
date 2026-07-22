package ru.abs7.b24support.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.api.dto.crm.CrmCategoryOption;
import ru.abs7.b24support.api.dto.crm.CrmIntegrationConfigRequest;
import ru.abs7.b24support.api.dto.crm.CrmIntegrationConfigResponse;
import ru.abs7.b24support.api.dto.crm.CrmSmartProcessOption;
import ru.abs7.b24support.api.dto.crm.CrmStageOption;
import ru.abs7.b24support.api.dto.crm.CrmValidationResponse;
import ru.abs7.b24support.service.CrmConfigurationService;

import java.util.List;

@RestController
@RequestMapping("/api/admin-portal/{portalId}/crm")
public class CrmIntegrationController {

    private final CrmConfigurationService service;

    public CrmIntegrationController(CrmConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public CrmIntegrationConfigResponse config(@PathVariable Long portalId) {
        return service.getConfig(portalId);
    }

    @GetMapping("/processes")
    public List<CrmSmartProcessOption> processes(@PathVariable Long portalId) {
        return service.listProcesses(portalId);
    }

    @GetMapping("/processes/{entityTypeId}/categories")
    public List<CrmCategoryOption> categories(@PathVariable Long portalId,
                                              @PathVariable Integer entityTypeId) {
        return service.listCategories(portalId, entityTypeId);
    }

    @GetMapping("/processes/{entityTypeId}/categories/{categoryId}/stages")
    public List<CrmStageOption> stages(@PathVariable Long portalId,
                                      @PathVariable Integer entityTypeId,
                                      @PathVariable Integer categoryId) {
        return service.listStages(portalId, entityTypeId, categoryId);
    }

    @PutMapping("/config")
    public CrmIntegrationConfigResponse save(@PathVariable Long portalId,
                                             @Valid @RequestBody CrmIntegrationConfigRequest request) {
        return service.saveConfig(portalId, request);
    }

    @PostMapping("/config/validate")
    public CrmValidationResponse validate(@PathVariable Long portalId) {
        return service.validate(portalId);
    }
}
