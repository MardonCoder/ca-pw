plugins {
    id("java")
}

group = "org.mardon"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val hibernateVersion = "7.4.17.Final"

dependencies {

    implementation("org.hibernate.orm:hibernate-core:$hibernateVersion")
    implementation("org.hibernate.orm:hibernate-jcache:$hibernateVersion")

    implementation("javax.cache:cache-api:1.1.1")

    implementation("org.ehcache:ehcache:3.12.0") {
        artifact {
            classifier = "jakarta"
        }
    }

    implementation("org.postgresql:postgresql:42.7.14")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}