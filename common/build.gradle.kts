plugins {
    `java-library`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    api("com.google.code.gson:gson:2.13.1")
    implementation("org.yaml:snakeyaml:2.7")
    implementation("com.zaxxer:HikariCP:7.0.2")
    runtimeOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.6")
}
