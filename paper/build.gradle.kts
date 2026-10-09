plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.jar { enabled = false }
tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.shadowJar {
    archiveBaseName.set("BetterChat-Paper")
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
