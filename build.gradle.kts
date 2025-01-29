import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

plugins {
	java
	application
	`maven-publish`
	signing
	id("com.github.ben-manes.versions") version "0.52.0"
	eclipse
}

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://oss.sonatype.org/content/repositories/snapshots")
	maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
}

group = "com.github.calimero"
version = "2.6-rc1"

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
	mainClass.set("tuwien.auto.calimero.gui.SwtChecker")
}

// SWT is platform dependent
val swtGroupId = "org.eclipse.platform"
val swtVersion = "3.127.0"
var swtArtifact = "org.eclipse.swt."

val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
swtArtifact += when {
	os.contains("windows") -> "win32.win32."
	os.contains("linux")   -> "gtk.linux."
	os.contains("mac")     -> "cocoa.macosx."
	else                   -> ""
}

val arch = System.getProperty("os.arch")
swtArtifact += when (arch) {
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
	options.compilerArgs = listOf(
		"-Xlint:all",
	)
}

configurations {
	create("provided")
	configurations.compileOnly.get().extendsFrom(configurations["provided"])
}

configurations.all {
	resolutionStrategy.dependencySubstitution {
		substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
			.using(module("$swtGroupId:$swtArtifact:$swtVersion"))
	}
}

dependencies {
	implementation("com.github.calimero:calimero-core:$version")
	implementation("com.github.calimero:calimero-tools:$version")
	runtimeOnly("com.github.calimero:calimero-tools:$version") {
		capabilities {
			requireCapability("com.github.calimero:calimero-tools-serial")
		}
	}
	runtimeOnly("com.github.calimero:calimero-tools:$version") {
		capabilities {
			requireCapability("com.github.calimero:calimero-tools-usb")
		}
	}
	implementation("$swtGroupId:$swtArtifact:$swtVersion")
	implementation("org.slf4j:slf4j-api:2.0.12")
	runtimeOnly("org.slf4j:slf4j-simple:2.0.12")
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
	withSourcesJar()
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
			"Class-Path" to (configurations.runtimeClasspath.get() - configurations["provided"] + files("swt.jar")).map { it.name }.joinToString(" ")
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

tasks.startScripts {
	doLast {
		// on OS X, SWT needs to run on first thread
		unixScript.writeText(unixScript.readText().replace("DEFAULT_JVM_OPTS='",
			"""
			MACOS_JVM_OPTS=""
			if [ "`uname`" = Darwin ] ; then
				MACOS_JVM_OPTS="-XstartOnFirstThread"
			fi
			DEFAULT_JVM_OPTS="${'$'}{MACOS_JVM_OPTS}"' """.trimIndent()))
	}
}

tasks.withType<JavaExec> {
	if (os.contains("mac")) {
		jvmArgs("-XstartOnFirstThread")
	}
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
			val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
			val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
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
