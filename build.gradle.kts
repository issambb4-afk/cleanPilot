import java.security.MessageDigest

plugins {
    java
    application
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.cleanpilot"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("com.cleanpilot.CleanPilotApplication")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")
    implementation("com.github.oshi:oshi-core:6.6.5")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.6.1.Final")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.3.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Exec>("packageAppImage") {
    group = "distribution"
    description = "Packages CleanPilot into a standalone portable Windows directory with embedded runtime"
    dependsOn(tasks.named("bootJar"))
    doFirst {
        val bootJarTask = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get()
        val jarFile = bootJarTask.archiveFile.get().asFile
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        val existingAppImage = File(distDir, "CleanPilot")
        if (existingAppImage.exists()) {
            existingAppImage.deleteRecursively()
        }
        distDir.mkdirs()
        val iconFile = File(projectDir, "src/main/resources/icons/cleanpilot.ico")
        val cmd = mutableListOf(
            "jpackage",
            "--type", "app-image",
            "--input", jarFile.parentFile.absolutePath,
            "--name", "CleanPilot",
            "--main-jar", jarFile.name,
            "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
            "--app-version", "1.0.0",
            "--vendor", "CleanPilot Team",
            "--java-options", "-XX:CICompilerCount=2",
            "--java-options", "-Dfile.encoding=UTF-8",
            "--dest", distDir.absolutePath
        )
        if (iconFile.exists()) {
            cmd.add("--icon")
            cmd.add(iconFile.absolutePath)
        }
        commandLine(cmd)
    }
}

tasks.register<Exec>("packageMsi") {
    group = "distribution"
    description = "Packages CleanPilot into a native Windows MSI/installer using jpackage (requires WiX Toolset v3.x)"
    dependsOn(tasks.named("bootJar"))
    doFirst {
        // WiX Toolset pre-check
        val pathEnv = System.getenv("PATH") ?: ""
        val hasWix = pathEnv.split(File.pathSeparator).any { dir ->
            File(dir, "candle.exe").exists() || File(dir, "light.exe").exists()
        }
        if (!hasWix) {
            logger.warn("WARNING: WiX Toolset v3.x (candle.exe, light.exe) was not found in PATH.")
            logger.warn("To build MSI installers, install WiX via: winget install WiX.Toolset.v3 OR choco install wixtoolset")
        }

        val bootJarTask = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get()
        val jarFile = bootJarTask.archiveFile.get().asFile
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        val existingMsi = File(distDir, "CleanPilot-1.0.0.msi")
        if (existingMsi.exists()) {
            existingMsi.delete()
        }
        distDir.mkdirs()
        val iconFile = File(projectDir, "src/main/resources/icons/cleanpilot.ico")
        val cmd = mutableListOf(
            "jpackage",
            "--type", "msi",
            "--input", jarFile.parentFile.absolutePath,
            "--name", "CleanPilot",
            "--main-jar", jarFile.name,
            "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
            "--app-version", "1.0.0",
            "--vendor", "CleanPilot Team",
            "--java-options", "-XX:CICompilerCount=2",
            "--java-options", "-Dfile.encoding=UTF-8",
            "--description", "CleanPilot - Honest & Privacy-First Disk Analyzer",
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut",
            "--dest", distDir.absolutePath
        )
        if (iconFile.exists()) {
            cmd.add("--icon")
            cmd.add(iconFile.absolutePath)
        }
        commandLine(cmd)
    }
}

tasks.register("generateDistributionManifests") {
    group = "distribution"
    description = "Calculates SHA-256 checksums of release binaries and updates package manager distribution manifests"
    doLast {
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        val jarFile = File(libsDir, "cleanpilot-1.0.0.jar")
        val appImageExe = File(distDir, "CleanPilot/CleanPilot.exe")
        val msiFile = File(distDir, "CleanPilot-1.0.0.msi")

        fun sha256Hex(file: File): String {
            if (!file.exists()) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { b -> "%02x".format(b) }.uppercase()
        }

        val jarHash = sha256Hex(jarFile)
        val exeHash = sha256Hex(appImageExe)
        val msiHash = if (msiFile.exists()) sha256Hex(msiFile) else exeHash

        logger.lifecycle("Artifact Checksums:")
        logger.lifecycle("  cleanpilot-1.0.0.jar: $jarHash")
        logger.lifecycle("  CleanPilot.exe:       $exeHash")
        if (msiFile.exists()) {
            logger.lifecycle("  CleanPilot-1.0.0.msi: $msiHash")
        }

        val chosenHash = if (msiFile.exists()) msiHash else exeHash

        // 1. Update Winget manifest
        val wingetInstaller = File(projectDir, "distribution/winget/CleanPilot.CleanPilot.installer.yaml")
        if (wingetInstaller.exists() && chosenHash.isNotBlank()) {
            var content = wingetInstaller.readText()
            content = content.replace("<CALCULATED_SHA256_HASH>", chosenHash)
            wingetInstaller.writeText(content)
            logger.lifecycle("Updated: ${wingetInstaller.relativeTo(projectDir).path}")
        }

        // 2. Update Chocolatey install script
        val chocoScript = File(projectDir, "distribution/chocolatey/tools/chocolateyinstall.ps1")
        if (chocoScript.exists() && chosenHash.isNotBlank()) {
            var content = chocoScript.readText()
            content = content.replace("<CALCULATED_SHA256_HASH>", chosenHash)
            chocoScript.writeText(content)
            logger.lifecycle("Updated: ${chocoScript.relativeTo(projectDir).path}")
        }

        // 3. Update Scoop manifest
        val scoopManifest = File(projectDir, "distribution/scoop/cleanpilot.json")
        if (scoopManifest.exists() && exeHash.isNotBlank()) {
            var content = scoopManifest.readText()
            content = content.replace("<CALCULATED_SHA256_HASH>", exeHash)
            scoopManifest.writeText(content)
            logger.lifecycle("Updated: ${scoopManifest.relativeTo(projectDir).path}")
        }
    }
}

tasks.register("signReleaseArtifacts") {
    group = "distribution"
    description = "Signs release executables and installers using signtool.exe and certificate from environment"
    doLast {
        val certPath = System.getenv("CERT_PATH")
        val certPassword = System.getenv("CERT_PASSWORD")
        val timestampServer = System.getenv("TIMESTAMP_URL") ?: "http://timestamp.digicert.com"

        if (certPath == null || !File(certPath).exists()) {
            logger.lifecycle("NOTE: CERT_PATH not provided or file not found. Skipping Authenticode signing.")
            logger.lifecycle("To sign binaries: set CERT_PATH=C:/path/to/cert.pfx and CERT_PASSWORD=secret")
            return@doLast
        }

        val distDir = layout.buildDirectory.dir("dist").get().asFile
        val targets = listOf(
            File(distDir, "CleanPilot/CleanPilot.exe"),
            File(distDir, "CleanPilot-1.0.0.msi")
        ).filter { it.exists() }

        targets.forEach { target ->
            logger.lifecycle("Signing ${target.name}...")
            val cmd = mutableListOf(
                "signtool.exe", "sign",
                "/f", certPath,
                "/tr", timestampServer,
                "/td", "sha256",
                "/fd", "sha256"
            )
            if (!certPassword.isNullOrBlank()) {
                cmd.addAll(listOf("/p", certPassword))
            }
            cmd.add(target.absolutePath)

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (proc.exitValue() == 0) {
                logger.lifecycle("Successfully signed: ${target.name}")
            } else {
                logger.error("Signing failed for ${target.name}: $output")
            }
        }
    }
}
