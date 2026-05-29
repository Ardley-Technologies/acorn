dependencies {
    api(project(":acorn-core"))

    implementation(libs.bundles.jackson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing)
}
