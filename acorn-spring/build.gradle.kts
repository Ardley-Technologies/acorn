dependencies {
    api(project(":acorn-core"))

    implementation(libs.spring.webmvc)
    implementation(libs.spring.context)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.log4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.spring.test)
}
