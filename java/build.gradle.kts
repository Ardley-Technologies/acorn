allprojects {
    group = "com.ardley.acorn"
    version = "0.2.2"

    repositories {
        mavenCentral()
    }

    configurations.all {
        resolutionStrategy {
            force("com.google.guava:guava:33.4.0-jre")
        }
    }
}

subprojects {
    if (name == "acorn-bom") return@subprojects

    plugins.apply("java-library")
    plugins.apply("maven-publish")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withJavadocJar()
        withSourcesJar()
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<Javadoc> {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:none", "-quiet")
            }
        }
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                pom {
                    name.set(project.name)
                    description.set("Declarative, schema-free RBAC for Java APIs")
                    url.set("https://github.com/Ardley-Technologies/acorn")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("ardley")
                            name.set("Ardley Technologies")
                            email.set("developers@ardley.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/Ardley-Technologies/acorn.git")
                        developerConnection.set("scm:git:ssh://github.com/Ardley-Technologies/acorn.git")
                        url.set("https://github.com/Ardley-Technologies/acorn")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Ardley-Technologies/acorn")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

}
