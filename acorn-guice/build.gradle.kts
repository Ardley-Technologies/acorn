dependencies {
    api(project(":acorn-core"))
    api(project(":acorn-jaxrs"))

    implementation(libs.guice)

    testImplementation(libs.jakarta.ws.rs.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing.mock)
}
