pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/spring-plugin") }
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "geekwaves-server"
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/spring") }
        maven {
            url = uri("https://maven.pkg.github.com/ItsukaKotori/itsuka-spring")
            val gprUser = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
            val gprToken = providers.gradleProperty("gpr.token")
                .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            if (gprUser != null && gprToken != null) {
                credentials {
                    username = gprUser
                    password = gprToken
                }
            }
        }
        mavenCentral()
    }
}
