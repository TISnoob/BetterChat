plugins {
    id("com.gradleup.shadow") version "9.3.2" apply false
}

group = "com.tis199.betterchat"
version = "0.1.0-SNAPSHOT"

allprojects {
    group = rootProject.group
    version = rootProject.version
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.lucko.me/")
    }
}

val generateFlagPack = tasks.register<Exec>("generateFlagPack") {
    group = "build"
    description = "Builds the BetterChat Minecraft Java resource pack with all ISO flags and the Earth glyph."
    workingDir = rootDir
    commandLine("python3", "resource-pack/generate_pack.py")
    inputs.dir("resource-pack/src")
    inputs.file("resource-pack/generate_pack.py")
    inputs.file("resource-pack/THIRD_PARTY_NOTICES.md")
    inputs.file("resource-pack/LICENSE-GRAPHICS")
    outputs.file("resource-pack/BetterChat-Flags.zip")
}

allprojects {
    tasks.matching { it.name == "assemble" }.configureEach {
        dependsOn(generateFlagPack)
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}
