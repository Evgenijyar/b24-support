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
}
