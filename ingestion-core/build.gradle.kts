plugins {
    id("suivons.java-library")
}

dependencies {
    api(project(":db"))
    api(project(":domain"))
}
