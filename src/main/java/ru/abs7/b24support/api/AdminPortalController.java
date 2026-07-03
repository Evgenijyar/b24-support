package ru.abs7.b24support.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.api.dto.admin.AdminPortalActionResponse;
import ru.abs7.b24support.api.dto.admin.AdminPortalSummaryResponse;
import ru.abs7.b24support.api.dto.admin.BitrixUserListResponse;
import ru.abs7.b24support.api.dto.admin.SupportUsersRequest;
import ru.abs7.b24support.service.AdminPortalService;

@RestController
@RequestMapping("/api/admin-portal")
public class AdminPortalController {

    private final AdminPortalService adminPortalService;

    public AdminPortalController(AdminPortalService adminPortalService) {
        this.adminPortalService = adminPortalService;
    }

    @GetMapping("/summary")
    public AdminPortalSummaryResponse summary() {
        return adminPortalService.summary();
    }

    @GetMapping("/{portalId}/users")
    public BitrixUserListResponse users(@PathVariable Long portalId) {
        return adminPortalService.users(portalId);
    }

    @PostMapping("/{portalId}/test-connection")
    public AdminPortalActionResponse testConnection(@PathVariable Long portalId) {
        return adminPortalService.testConnection(portalId);
    }

    @PostMapping("/{portalId}/load-users")
    public AdminPortalActionResponse loadUsers(@PathVariable Long portalId) {
        return adminPortalService.loadUsers(portalId);
    }

    @PutMapping("/{portalId}/support-users")
    public BitrixUserListResponse saveSupportUsers(@PathVariable Long portalId,
                                                   @RequestBody SupportUsersRequest request) {
        return adminPortalService.saveSupportUsers(portalId, request.userIds());
    }

    @PostMapping("/{portalId}/bot/register")
    public AdminPortalActionResponse registerBot(@PathVariable Long portalId) {
        return adminPortalService.registerBot(portalId);
    }

    @PostMapping("/{portalId}/routing/repair")
    public AdminPortalActionResponse repairRouting(@PathVariable Long portalId) {
        return adminPortalService.repairRouting(portalId);
    }

    @PostMapping("/{portalId}/chat/create")
    public AdminPortalActionResponse createSupportChat(@PathVariable Long portalId) {
        return adminPortalService.createSupportChat(portalId);
    }

    @PostMapping("/{portalId}/chat/add-users")
    public AdminPortalActionResponse addSupportUsersToChat(@PathVariable Long portalId) {
        return adminPortalService.addSupportUsersToChat(portalId);
    }

    @PostMapping("/{portalId}/chat/test-message")
    public AdminPortalActionResponse sendTestMessage(@PathVariable Long portalId) {
        return adminPortalService.sendTestMessage(portalId);
    }
}
