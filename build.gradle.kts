plugins {
    id("com.gradleup.shadow") version "9.3.2" apply false
}

group = "com.tis199.betterchat"
version = "0.1.0-pre.1"

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

val preReleaseDirectory = layout.buildDirectory.dir("pre-release/$version")

val preparePreRelease = tasks.register<Sync>("preparePreRelease") {
    group = "distribution"
    description = "Builds the BetterChat pre-release assets and collects the three plugins with the resource pack."
    dependsOn(":paper:shadowJar", ":bungeecord:shadowJar", ":velocity:shadowJar", generateFlagPack)

    into(preReleaseDirectory)
    from(project(":paper").layout.buildDirectory.dir("libs")) {
        include("BetterChat-Paper-$version.jar")
    }
    from(project(":bungeecord").layout.buildDirectory.dir("libs")) {
        include("BetterChat-BungeeCord-$version.jar")
    }
    from(project(":velocity").layout.buildDirectory.dir("libs")) {
        include("BetterChat-Velocity-$version.jar")
    }
    from(layout.projectDirectory.dir("resource-pack")) {
        include("BetterChat-Flags.zip")
        rename("BetterChat-Flags.zip", "BetterChat-Flags-$version.zip")
    }
    from(layout.projectDirectory.file("docs/RELEASE-0.1.0-pre.1.md")) {
        rename { "RELEASE-NOTES.md" }
    }

    val checksumsFile = preReleaseDirectory.map { it.file("SHA256SUMS") }
    outputs.file(checksumsFile)
    doLast {
        val directory = preReleaseDirectory.get().asFile
        val assets = listOf(
            "BetterChat-Paper-$version.jar",
            "BetterChat-BungeeCord-$version.jar",
            "BetterChat-Velocity-$version.jar",
            "BetterChat-Flags-$version.zip",
        )
        val checksums = assets.joinToString(separator = "\n", postfix = "\n") { asset ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(directory.resolve(asset).readBytes())
            "${digest.joinToString("") { byte -> "%02x".format(byte) }}  $asset"
        }
        checksumsFile.get().asFile.writeText(checksums)
    }
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
