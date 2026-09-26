// API REST en lecture (+ signalements) (SPEC.md §8).
plugins {
    id("suivons.spring-boot-app")
}

dependencies {
    implementation(project(":db"))
    implementation(project(":domain"))
    implementation(project(":contract"))
    implementation(project(":referentiel-client"))
}
