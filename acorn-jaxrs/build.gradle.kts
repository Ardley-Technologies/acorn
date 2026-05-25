dependencies {
    api(project(":acorn-core"))

    implementation(libs.jakarta.ws.rs.api)
    implementation(libs.jakarta.inject.api)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.log4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing.mock)
}

tasks.compileTestJava {
    options.compilerArgs.add("-parameters")
}
