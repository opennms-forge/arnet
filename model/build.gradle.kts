plugins {
    kotlin("jvm") version "1.3.61"
    `maven-publish`
}

group = "org.opennms.arnet"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "model"
            from(components["java"])
        }
    }
}