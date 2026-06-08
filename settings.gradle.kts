pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // OpenSAML artifacts (pulled in by spring-security-saml2-service-provider)
        // are not published to Maven Central; they live in the Shibboleth repo.
        maven {
            name = "shibboleth"
            url = uri("https://build.shibboleth.net/maven/releases/")
            content {
                includeGroupByRegex("org\\.opensaml")
                includeGroup("net.shibboleth")
            }
        }
    }
}

rootProject.name = "sopstore"

// Standalone, independently-deployable versioned script repository (own DB + API).
include("script-service")
