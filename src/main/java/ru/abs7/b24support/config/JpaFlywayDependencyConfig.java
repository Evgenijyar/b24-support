package ru.abs7.b24support.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JpaFlywayDependencyConfig {

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
                return;
            }
            if (!beanFactory.containsBeanDefinition("flyway")) {
                return;
            }

            BeanDefinition entityManagerFactory = beanFactory.getBeanDefinition("entityManagerFactory");
            entityManagerFactory.setDependsOn("flyway");
        };
    }
}
