plugins {
    id("com.gradleup.shadow") version "9.3.2" apply false
}

group = "com.tis199.betterchat"
version = "1.1.3"

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

val releaseDirectory = layout.buildDirectory.dir("release/$version")

val prepareRelease = tasks.register<Sync>("prepareRelease") {
    group = "distribution"
    description = "Builds the BetterChat release assets and collects the three plugins with the resource pack."
    dependsOn(":paper:shadowJar", ":bungeecord:shadowJar", ":velocity:shadowJar", generateFlagPack)

    into(releaseDirectory)
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
    from(layout.projectDirectory.file("docs/RELEASE-$version.md")) {
        rename { "RELEASE-NOTES.md" }
    }

    val checksumsFile = releaseDirectory.map { it.file("SHA256SUMS") }
    val resourcePackSha1File = releaseDirectory.map { it.file("RESOURCE-PACK-SHA1.txt") }
    outputs.file(checksumsFile)
    outputs.file(resourcePackSha1File)
    doLast {
        val directory = releaseDirectory.get().asFile
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
        val resourcePackSha1 = java.security.MessageDigest.getInstance("SHA-1")
            .digest(directory.resolve("BetterChat-Flags-$version.zip").readBytes())
        resourcePackSha1File.get().asFile.writeText(
            "${resourcePackSha1.joinToString("") { byte -> "%02x".format(byte) }}\n"
        )
    }
}

val preparePreRelease = tasks.register("preparePreRelease") {
    group = "distribution"
    description = "Alias for prepareRelease"
    dependsOn(prepareRelease)
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
