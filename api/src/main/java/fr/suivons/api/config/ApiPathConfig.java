package fr.suivons.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Préfixe des contrôleurs de l'API, égal au serveur déclaré dans le contrat (SPEC.md §8).
 * Actuator reste hors préfixe (/actuator).
 */
@Configuration(proxyBeanMethods = false)
public class ApiPathConfig implements WebMvcConfigurer {

    public static final String BASE_PATH = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                BASE_PATH,
                HandlerTypePredicate.forBasePackage("fr.suivons.api")
                        .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
