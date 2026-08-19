plugins {
    `java-platform`
    `maven-publish`
    signing
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":acorn-core"))
        api(project(":acorn-roles"))
        api(project(":acorn-jaxrs"))
        api(project(":acorn-guice"))
        api(project(":acorn-spring"))
        api(project(":acorn-cdi"))
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Ardley-Technologies/acorn")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "MavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])

            pom {
                name.set("Acorn BOM")
                description.set("Bill of Materials for Acorn authorization modules")
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
}

signing {
    isRequired = System.getenv("CI") != null
    useInMemoryPgpKeys(
        System.getenv("GPG_KEY_ID"),
        System.getenv("GPG_PRIVATE_KEY"),
        System.getenv("GPG_PASSPHRASE")
    )
    sign(publishing.publications["mavenBom"])
}
