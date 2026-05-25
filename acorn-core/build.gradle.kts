dependencies {
    implementation(libs.guava)
    implementation(libs.bundles.jackson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing)
}
