plugins {
    id("suivons.java-library")
}

dependencies {
    api(project(":domain"))

    implementation(libs.spring.web)
    implementation(libs.spring.context)
    implementation(libs.spring.boot)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.caffeine)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.ratelimiter)

    testImplementation(libs.spring.boot.test)
    integrationTestImplementation(libs.wiremock)
}
