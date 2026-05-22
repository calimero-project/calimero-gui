import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.jar.JarFile

plugins {
	java
	application
	`maven-publish`
	signing
	id("org.graalvm.buildtools.native") version "1.1.0"
	id("com.github.ben-manes.versions") version "0.54.0"
	eclipse
}

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://central.sonatype.com/repository/maven-snapshots/")
}

group = "io.calimero"
version = "3.0-SNAPSHOT"

fun date(): String = SimpleDateFormat("yyyyMMdd").format(Date())
val buildClassifier = date()

tasks.named<Zip>("distZip") {
	archiveClassifier.set(buildClassifier)
}

tasks.named<Tar>("distTar") {
	archiveClassifier.set(buildClassifier)
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
	options.encoding = "UTF-8"
}

application {
	mainModule.set("io.calimero.gui")
	mainClass.set("io.calimero.gui.SwtChecker")
}

val os = org.gradle.internal.os.OperatingSystem.current()!!
val arch = System.getProperty("os.arch")!!

// SWT is platform dependent
val swtGroupId = "org.eclipse.platform"
val swtVersion = "3.133.0"
var swtArtifact = "org.eclipse.swt." + when {
	os.isWindows -> "win32.win32."
	os.isLinux   -> "gtk.linux."
	os.isMacOsX  -> "cocoa.macosx."
	else -> error("unsupported OS $os")
} + when (arch) {
	"aarch64"         -> "aarch64"
	"amd64", "x86_64" -> "x86_64"
	else              -> "x86"
}

sourceSets {
	main {
		java.srcDirs("src")
		resources.srcDir("resources")
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs = listOf("-Xlint:all")
}

tasks.named<JavaCompile>("compileJava") {
	options.javaModuleVersion = version.toString()
}

configurations {
	create("provided")
	configurations.compileOnly.get().extendsFrom(configurations["provided"])
}

configurations.all {
	resolutionStrategy.dependencySubstitution {
		substitute(module($$"org.eclipse.platform:org.eclipse.swt.${osgi.platform}"))
			.using(module("$swtGroupId:$swtArtifact:$swtVersion"))
	}
}

dependencies {
	implementation("io.calimero:calimero-core:$version")
	implementation("io.calimero:calimero-tools:$version") {
		exclude(group = "org.slf4j")
	}
	runtimeOnly("io.calimero:calimero-tools:$version") {
		capabilities {
			requireCapability("io.calimero:calimero-tools-serial")
		}
	}
	runtimeOnly("io.calimero:calimero-tools:$version") {
		capabilities {
			requireCapability("io.calimero:calimero-tools-usb")
		}
	}
	implementation("$swtGroupId:$swtArtifact:$swtVersion")
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
	withSourcesJar()
	withJavadocJar()
}

tasks.withType<Jar>().configureEach {
	from(project.projectDir) {
		include("LICENSE.txt")
		into("META-INF")
	}
	if (name == "sourcesJar") {
		from(project.projectDir) {
			include("README.md")
		}
	}
}

tasks.named<Jar>("jar") {
	dependsOn(configurations.runtimeClasspath)
	manifest {
		val gitHash = providers.exec {
			commandLine("git", "-C", project.projectDir.toString(), "rev-parse", "--verify", "--short", "HEAD")
		}.standardOutput.asText.map { it.trim() }
		val buildDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
			.withZone(ZoneId.of("UTC"))
			.format(Instant.now())

		attributes(
			"Main-Class" to application.mainClass.get(),
			"Implementation-Version" to project.version,
			"Revision" to gitHash.get(),
			"Build-Date" to buildDate,
			"Class-Path" to (configurations.runtimeClasspath.get() - configurations["provided"] + files("swt.jar")).joinToString(" ") { it.name }
		)
	}
}

application {
	applicationDistribution.from(project.projectDir) {
		include("LICENSE.txt")
	}
}

distributions {
	main {
		contents {
			exclude(configurations["provided"].map { it.name })
		}
	}
}

val addReads = listOf(
	"--add-reads", "io.calimero.core=io.calimero.tools", // @LinkEvent
	"--add-reads", "io.calimero.tools=ALL-UNNAMED", // zip4j
	"--add-reads", "io.calimero.serial.provider.rxtx=ALL-UNNAMED",
	"--add-reads", "io.calimero.usb.provider.javax=ALL-UNNAMED" // javax.usb:usb-api
)

// avoid jvm warning about native access
val enableNativeAccess = listOf(
	"--enable-native-access=io.calimero.serial.provider.jni,serial.ffm,org.usb4java,$swtArtifact",
	"--enable-native-access=ALL-UNNAMED", // libs used by rxtx
)

tasks.startScripts {
	defaultJvmOpts = addReads + enableNativeAccess

	doLast {
		fun File.replace(replace: String, with: String) = writeText(readText().replace(replace, with))

		// on OS X, SWT needs to run on first thread
		unixScript.replace("DEFAULT_JVM_OPTS='",
			$$"""
			MACOS_JVM_OPTS=""
			if [ "`uname`" = Darwin ] ; then
				MACOS_JVM_OPTS="-XstartOnFirstThread"
			fi
			DEFAULT_JVM_OPTS="${MACOS_JVM_OPTS}"' """.trimIndent())
		// add dependency on downloaded swt.jar (adding files('swt.jar') to classpath doesn't work)
		unixScript.replace($$"MODULE_PATH=$APP_HOME", $$"MODULE_PATH=$APP_HOME/lib/swt.jar:$APP_HOME")
		windowsScript.replace("MODULE_PATH=%APP_HOME%", "MODULE_PATH=%APP_HOME%\\lib\\swt.jar;%APP_HOME%")
	}
}

tasks.withType<JavaExec>().configureEach {
	jvmArgs(addReads)
	jvmArgs(enableNativeAccess)
	if (os.isMacOsX) {
		jvmArgs("-XstartOnFirstThread")
	}
}

tasks.named<JavaExec>("run") {
	// Work around https://github.com/graalvm/native-build-tools/issues/743
	outputs.upToDateWhen { false }
}

val appName = "Calimero"

// graalvm native image uses jdk 25, so we can include serial-ffm which requires java 23
val nativeImageSerialFfm by configurations.creating
dependencies {
	nativeImageSerialFfm("io.calimero:calimero-serial-ffm:$version")
}

graalvmNative {
//	toolchainDetection.set(true) // only works reliably if a single JDK is installed, which is GraalVM
	agent {
//		enabled = true
		defaultMode = "standard"
	}
	binaries {
		named("main") {
//			verbose = true
			mainClass.set(appName) // yes, this sets the output name for some reason

			val modulePathJars = (classpath.files + nativeImageSerialFfm.files).filter { file ->
				file.exists() && file.name.endsWith(".jar") &&
						JarFile(file).use { jar ->
							jar.getEntry("module-info.class") != null ||
									jar.manifest?.mainAttributes?.getValue("Automatic-Module-Name") != null
						}
			}
			buildArgs.addAll(
				listOf(
					"--module-path", modulePathJars.joinToString(File.pathSeparator),
					"--module", "io.calimero.gui/io.calimero.gui.Main",
					"--enable-sbom=export",
					"--future-defaults=all",
					"--emit build-report",
					"--initialize-at-build-time",
					"-march=native",
					"-Os",
					"--no-fallback",
					"--exact-reachability-metadata",
					"-H:+ReportExceptionStackTraces",
					"-H:+UnlockExperimentalVMOptions",
					"-H:-EnableLoggingFeature",
				)
			)
			buildArgs.addAll(addReads)
			buildArgs.addAll(enableNativeAccess)
		}
	}
}

val packageDir = layout.buildDirectory.dir("package")
val runtimeDir = packageDir.map { it.dir("runtime") }
val appDir = packageDir.map { it.dir("app") }
val jarTask = tasks.named<Jar>("jar")

abstract class JdepsTask : DefaultTask() {
	@get:InputFiles
	abstract val modules: ConfigurableFileCollection
	@get:OutputFile
	abstract val outputFile: RegularFileProperty
	@get:Inject
	abstract val execOperations: ExecOperations

	@TaskAction
	fun jdeps() {
		val result = ByteArrayOutputStream()
		execOperations.exec {
			val jdeps = listOf("jdeps",
				"--print-module-deps", "--ignore-missing-deps", "--recursive", "-quiet",
				"--module-path", modules.asPath
			) + modules.files.map { it.path }
			commandLine(jdeps)
			standardOutput = result
		}
		outputFile.get().asFile.writeText(result.toString().trim())
	}
}

val jdepsTask = tasks.register<JdepsTask>("jdeps") {
	group = "other"
	description = "Finds the module dependencies for the main binary"
	dependsOn("jar")

	modules.from(configurations.runtimeClasspath.get().filter { it.exists() }, jarTask)
	outputFile.set(packageDir.get().file("jdeps.out"))
}

tasks.register<Delete>("cleanRuntime") {
	delete(runtimeDir)
}

tasks.register<Exec>("runtime") {
	group = "build"
	description = "Creates a Java runtime for the main binary"
	dependsOn("cleanRuntime")
	val jdepsOutput = jdepsTask.flatMap { it.outputFile }
	inputs.file(jdepsOutput)

	val output = runtimeDir.get().asFile.path
	doFirst {
		val jdkModules = jdepsOutput.get().asFile.readText().replace(",java.logging", "")
		val jlink = listOf("jlink",
			"--no-header-files", "--no-man-pages", "--strip-debug", "--strip-native-commands",
			"--compress", "zip-9",
			"--output", output,
			"--add-modules", jdkModules,
			"--limit-modules", jdkModules,
		)
		commandLine(jlink)
	}
}

tasks.register<Copy>("preparePackageJars") {
	dependsOn("jar")
	from(configurations.runtimeClasspath, jarTask)
	into(packageDir.get().dir("libs"))
	exclude(
		when {
			os.isLinux   -> listOf("darwin", "win")
			os.isMacOsX  -> listOf("linux", "win")
			os.isWindows -> listOf("darwin", "linux")
			else         -> error("unsupported OS $os")
		}.map { "libusb4java-*-$it*.jar" }
	)
	exclude(
		when (arch) {
			"aarch64"         -> listOf("x86*", "arm*")
			"amd64", "x86_64" -> listOf("aarch64*", "arm*", "x86") // no asterisk on x86 to not match x86_64
			else              -> listOf("aarch64*", "arm*", "x86_64*")
		}.map { "libusb4java-*$it.jar" }
	)
}

tasks.register<Copy>("copySerialNativeLib") {
	val serialNativeDir = file("../serial-native/bin/")
	from(serialNativeDir) {
		val libArch = if (arch == "aarch64") "aarch64" else "x86_64"
		include(when {
			os.isLinux   -> "linux-$libArch/libserialcom.so"
			os.isMacOsX  -> "darwin-$libArch/libserialcom.dylib"
			os.isWindows -> "win-$libArch/serialcom.dll"
			else         -> error("unsupported OS $os")
		})
		eachFile { path = name }
		includeEmptyDirs = false
	}
	into(packageDir.get().dir(
		when {
			os.isMacOsX -> "native/Frameworks"
			else        -> "native"
		})
	)
}

tasks.register<Delete>("cleanPackageApp") {
	delete(appDir)
}

tasks.register<Exec>("package") {
	group = "build"
	description = "Packages a self-contained Java application for the main binary"
	dependsOn("runtime", "cleanPackageApp", "preparePackageJars", "copySerialNativeLib")
	finalizedBy(if (os.isWindows) "zipAppImage" else "tarAppImage") // for Linux/Win, where jpackage creates an app folder

	val baseArgs = listOf("jpackage",
		"--type", "app-image",
		"--name", appName,
		"--description", "KNX Communication & Management",
		"--runtime-image", runtimeDir.get().asFile.path,
		"--module", application.mainModule.get(),
		"--module-path", packageDir.get().dir("libs").asFile.absolutePath,
		"--app-version", version.toString().substringBefore("-"),
		"--dest", appDir.get().asFile,
	)

	val javaOptionArgs = (
			enableNativeAccess +
			listOf(
				"--add-reads=io.calimero.core=io.calimero.tools", // @LinkEvent
				"--add-reads=io.calimero.serial.provider.rxtx=nrjavaserial",
				"--add-reads=io.calimero.usb.provider.javax=usb.api"
			) +
			if (os.isMacOsX) listOf("-XstartOnFirstThread") else listOf()
	).flatMap { listOf("--java-options", it) }

	val nativeDir = provider { packageDir.get().dir("native") }
	doFirst {
		val nativeArgs =
			if (!nativeDir.get().asFile.exists()) emptyList()
            else {
				val libDir = if (os.isMacOsX) "Frameworks" else "."
				val libPath = if (os.isMacOsX) "\$APPDIR/../Frameworks" else "\$APPDIR/../native"
                listOf(
                    "--app-content", nativeDir.get().dir(libDir).asFile.path,
                    "--java-options", "-Djava.library.path=$libPath"
                )
            }

		commandLine(baseArgs + javaOptionArgs + nativeArgs)
	}
}

tasks.register<Zip>("zipAppImage") {
	dependsOn("package")
	from(appDir.get().dir(appName))
	archiveFileName.set("$appName.zip")
	destinationDirectory.set(file(appDir))
}

tasks.register<Tar>("tarAppImage") {
	dependsOn("package")
	from(appDir.get().dir(appName)) {
		filesMatching("**/$appName") { // preserve executable bit
			permissions { unix("rwxr-xr-x") }
		}
	}
	archiveFileName.set("$appName.tar.gz")
	destinationDirectory.set(appDir)
	compression = Compression.GZIP
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = rootProject.name
			from(components["java"])
			pom {
				name.set("Calimero GUI")
				description.set("A graphical user interface for the Calimero tools collection")
				url.set("https://github.com/calimero-project/calimero-gui")
				inceptionYear.set("2006")
				licenses {
					license {
						name.set("GNU General Public License, version 2, with the Classpath Exception")
						url.set("LICENSE.txt")
					}
				}
				developers {
					developer {
						name.set("Boris Malinowsky")
						email.set("b.malinowsky@gmail.com")
					}
				}
				scm {
					connection.set("scm:git:git://github.com/calimero-project/calimero-gui.git")
					url.set("https://github.com/calimero-project/calimero-gui.git")
				}
			}
		}
	}
	repositories {
		maven {
			name = "maven"
			val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
			val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
			url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
			credentials(PasswordCredentials::class)
		}
	}
}

signing {
	if (project.hasProperty("signing.keyId")) {
		sign(publishing.publications["mavenJava"])
	}
}
