plugins {
    id("suivons.java-library")
}

dependencies {
    api(project(":domain"))
    implementation(project(":db"))
    implementation(project(":referentiel-client"))
}
