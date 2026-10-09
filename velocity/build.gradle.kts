plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common")) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    compileOnly("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    implementation("org.bstats:bstats-velocity:3.2.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.jar { enabled = false }
tasks.shadowJar {
    archiveBaseName.set("BetterChat-Velocity")
    archiveClassifier.set("")
    relocate("org.bstats", "${project.group}.lib.bstats")
    relocate("com.zaxxer.hikari", "${project.group}.lib.hikari")
    relocate("org.sqlite", "${project.group}.lib.sqlite")
    relocate("org.mariadb", "${project.group}.lib.mariadb")
    relocate("com.google.gson", "${project.group}.lib.gson")
    relocate("org.yaml.snakeyaml", "${project.group}.lib.snakeyaml")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
tasks.build { dependsOn(tasks.shadowJar) }
