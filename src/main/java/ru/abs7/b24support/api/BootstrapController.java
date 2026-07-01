package ru.abs7.b24support.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.service.BootstrapStatusService;
import java.util.Map;

@RestController
@RequestMapping("/api/bootstrap")
public class BootstrapController {

    private final BootstrapStatusService bootstrapStatusService;

    public BootstrapController(BootstrapStatusService bootstrapStatusService) {
        this.bootstrapStatusService = bootstrapStatusService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return bootstrapStatusService.buildStatus();
    }
}
