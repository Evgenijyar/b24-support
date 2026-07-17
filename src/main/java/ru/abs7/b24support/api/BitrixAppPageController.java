package ru.abs7.b24support.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class BitrixAppPageController {

    private final String installPage;
    private final String widgetPage;

    public BitrixAppPageController() {
        this.installPage = readResource("bitrix-app/install.html");
        this.widgetPage = readResource("bitrix-app/widget.html");
    }

    @RequestMapping(
            value = {"/bitrix/app", "/bitrix/app/install"},
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> install() {
        return html(installPage);
    }

    @RequestMapping(
            value = "/bitrix/app/widget",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> widget() {
        return html(widgetPage);
    }

    private ResponseEntity<String> html(String body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private String readResource(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить страницу Bitrix24-приложения: " + path, e);
        }
    }
}
