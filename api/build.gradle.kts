// API REST en lecture (+ signalements) (SPEC.md §8).
plugins {
    id("suivons.spring-boot-app")
}

dependencies {
    implementation(project(":db"))
    implementation(project(":domain"))
    implementation(project(":contract"))
    implementation(project(":referentiel-client"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    // Contraintes du contrat (paramètres) appliquées par la validation de méthode de Spring MVC
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jooq)
    runtimeOnly(libs.postgresql)

    // TI sur PostgreSQL (Testcontainers, Flyway en TI seulement) : fournis par le plugin suivons.spring-boot-app
    integrationTestImplementation(libs.snakeyaml)
    integrationTestImplementation(libs.swagger.request.validator)
}
