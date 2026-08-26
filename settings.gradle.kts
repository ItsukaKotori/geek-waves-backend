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
            url = uri("http://nexus.local:8081/repository/maven-snapshots/")
            isAllowInsecureProtocol = true
            credentials {
                username = providers.gradleProperty("nexusUsername").getOrElse("admin")
                password = providers.gradleProperty("nexusPassword").get()
            }
        }
        mavenCentral()
    }
}
