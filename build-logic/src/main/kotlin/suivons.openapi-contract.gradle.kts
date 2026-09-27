// Contrat d'API (API-first, SPEC.md §8) : validation de openapi.yaml, génération des interfaces
// Spring (compilées dans ce module) et du client Angular (consommé par /front).
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    id("suivons.java-library")
    id("org.openapi.generator")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val spec = layout.projectDirectory.file("openapi.yaml")
val springDir = layout.buildDirectory.dir("generated/openapi-spring")
val typescriptDir = layout.buildDirectory.dir("generated/typescript-angular")

// Le schéma Problem du contrat (RFC 9457) est porté côté Spring par ProblemDetail, pas par un DTO généré
val problemMapping = mapOf("Problem" to "org.springframework.http.ProblemDetail")

val validateContract = tasks.named<ValidateTask>("openApiValidate") {
    inputSpec = spec
    recommend = true
}

val generateSpring = tasks.named<GenerateTask>("openApiGenerate") {
    description = "Génère les interfaces Spring et les DTO depuis openapi.yaml."
    generatorName = "spring"
    inputSpec = spec
    outputDir = springDir
    cleanupOutput = true
    apiPackage = "fr.suivons.contract.api"
    modelPackage = "fr.suivons.contract.model"
    schemaMappings = problemMapping
    importMappings = problemMapping
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "useTags" to "true",
        "useSpringBoot4" to "true",
        "useJackson3" to "true",
        "useBeanValidation" to "true",
        "openApiNullable" to "false",
        "annotationLibrary" to "none",
        "documentationProvider" to "none",
        "sourceFolder" to "src/main/java",
        // Comportement explicite (sinon avertissement) : inclusion et nulls gérés par l'ObjectMapper global
        "generateJsonIncludeAnnotations" to "false",
        "generateJsonSetterNullsAnnotations" to "false",
    )
}

val generateTypescript = tasks.register<GenerateTask>("openApiGenerateTypescript") {
    group = "openapi tools"
    description = "Génère le client Angular depuis openapi.yaml (consommé par /front)."
    generatorName = "typescript-angular"
    inputSpec = spec
    outputDir = typescriptDir
    cleanupOutput = true
    configOptions = mapOf(
        "ngVersion" to "22.2.0",
        "providedIn" to "root",
        "fileNaming" to "kebab-case",
        "stringEnums" to "true",
    )
}

sourceSets.named("main") {
    java.srcDir(springDir.map { it.dir("src/main/java") })
}

tasks.named("compileJava") {
    dependsOn(generateSpring)
}

// Le contrat voyage dans le jar (classpath:openapi/openapi.yaml) : validation des réponses en TI
tasks.named<ProcessResources>("processResources") {
    from(spec) {
        into("openapi")
    }
}

tasks.named("check") {
    dependsOn(validateContract)
}

tasks.named("assemble") {
    dependsOn(generateTypescript)
}

dependencies {
    "api"(libs.findLibrary("spring-web").get())
    "api"(libs.findLibrary("spring-context").get())
    "api"(libs.findLibrary("jakarta-validation").get())
    "api"(libs.findLibrary("jackson-annotations").get())
    "compileOnly"(libs.findLibrary("jakarta-annotation").get())
    "compileOnly"(libs.findLibrary("jakarta-servlet").get())
    "testImplementation"(libs.findLibrary("snakeyaml").get())
}

// Le code généré utilise org.springframework.lang.Nullable, déprécié depuis Spring 7 (JSpecify) :
// avertissement propre au générateur, sans action possible dans ce module (code 100 % généré).
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Xlint:-deprecation")
}
