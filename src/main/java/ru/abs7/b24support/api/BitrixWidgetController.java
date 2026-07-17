package ru.abs7.b24support.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.api.dto.widget.WidgetActionRequest;
import ru.abs7.b24support.api.dto.widget.WidgetActionResponse;
import ru.abs7.b24support.api.dto.widget.WidgetContextRequest;
import ru.abs7.b24support.api.dto.widget.WidgetContextResponse;
import ru.abs7.b24support.service.SupportWidgetService;

@RestController
@RequestMapping("/api/bitrix/widget")
public class BitrixWidgetController {

    private final SupportWidgetService widgetService;

    public BitrixWidgetController(SupportWidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @PostMapping("/context")
    public WidgetContextResponse context(@Valid @RequestBody WidgetContextRequest request) {
        return widgetService.context(request);
    }

    @PostMapping("/action")
    public WidgetActionResponse action(@Valid @RequestBody WidgetActionRequest request) {
        return widgetService.execute(request);
    }
}
