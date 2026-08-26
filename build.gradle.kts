plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "org.geekwaves"
version = "0.1.0-SNAPSHOT"
description = "GeekWaves 程序员技术资讯站后端"
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
dependencies {
    implementation("org.itsuka:itsuka-spring-boot-starter:1.0.0-SNAPSHOT")
    implementation("org.itsuka:itsuka-web-spring-boot-starter:1.0.0-SNAPSHOT")
    implementation("org.itsuka:itsuka-mybatis-plus-spring-boot-starter:1.0.0-SNAPSHOT")
    implementation("org.itsuka:itsuka-redis-spring-boot-starter:1.0.0-SNAPSHOT")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.h2database:h2")
    implementation("org.flywaydb:flyway-core")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.github.oshi:oshi-core:7.6.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.rometools:rome:2.1.0")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
tasks.test {
    useJUnitPlatform()
}
