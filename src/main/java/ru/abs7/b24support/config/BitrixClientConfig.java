package ru.abs7.b24support.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.abs7.b24support.bitrix.BitrixRestClient;

@Configuration
public class BitrixClientConfig {

    @Bean
    public BitrixRestClient bitrixRestClient(ObjectMapper objectMapper) {
        return new BitrixRestClient(objectMapper);
    }
}
