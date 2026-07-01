package ru.abs7.b24support.service;

import org.springframework.stereotype.Service;
import ru.abs7.b24support.config.AppProperties;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BootstrapStatusService {

    private final AppProperties appProperties;
    private final PortalInstallationRepository portalInstallationRepository;

    public BootstrapStatusService(AppProperties appProperties,
                                  PortalInstallationRepository portalInstallationRepository) {
        this.appProperties = appProperties;
        this.portalInstallationRepository = portalInstallationRepository;
    }

    public Map<String, Object> buildStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("application", appProperties.getApplicationName());
        status.put("publicBaseUrl", appProperties.getPublicBaseUrl());
        status.put("time", OffsetDateTime.now().toString());
        status.put("portalCount", portalInstallationRepository.count());
        status.put("state", "BOOTSTRAP_OK");
        return status;
    }
}
