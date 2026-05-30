dependencies {
    api(project(":acorn-core"))

    implementation(libs.jakarta.cdi.api)
    implementation(libs.jakarta.inject.api)
    implementation(libs.jakarta.interceptor.api)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.log4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing.mock)
}
