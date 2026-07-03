package ru.abs7.b24support.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.api.dto.client.ClientPortalActionResponse;
import ru.abs7.b24support.api.dto.client.SupportMessageListResponse;
import ru.abs7.b24support.service.ClientPortalService;

@RestController
@RequestMapping("/api/client-portals")
public class ClientPortalController {

    private final ClientPortalService clientPortalService;

    public ClientPortalController(ClientPortalService clientPortalService) {
        this.clientPortalService = clientPortalService;
    }

    @GetMapping("/messages")
    public SupportMessageListResponse recentMessages() {
        return clientPortalService.recentMessages();
    }

    @PostMapping("/{portalId}/test-connection")
    public ClientPortalActionResponse testConnection(@PathVariable Long portalId) {
        return clientPortalService.testConnection(portalId);
    }

    @PostMapping("/{portalId}/bot/register")
    public ClientPortalActionResponse registerClientBot(@PathVariable Long portalId) {
        return clientPortalService.registerClientBot(portalId);
    }

    @PostMapping("/{portalId}/routing/repair")
    public ClientPortalActionResponse repairRouting(@PathVariable Long portalId) {
        return clientPortalService.repairRouting(portalId);
    }
}
