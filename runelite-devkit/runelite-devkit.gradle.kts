/*
 * Copyright (c) 2019 Owain van Brakel <https://github.com/Owain94>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

description = "RuneLite Devkit"
plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
    scala
}

repositories {
    maven {
        url = uri("https://repo.runelite.net")
    }
    mavenCentral()
    mavenLocal()
}

val scalaMajorVersion = '3'
val scalaVersion = "$scalaMajorVersion.4.0"
application {
    mainClass.set("net.runelite.devkit.Launcher")
}
dependencies {
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = ProjectVersions.lombokVersion)
    compileOnly(group = "org.projectlombok", name = "lombok", version = ProjectVersions.lombokVersion)
    implementation("org.scala-lang", "scala3-library_"+scalaMajorVersion, ""+scalaVersion)

    implementation(group = "com.google.inject", name = "guice", version = "5.0.1")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.32")
    implementation(group = "org.pf4j", name = "pf4j", version = "3.6.0") {
        exclude(group = "org.slf4j")
    }
    implementation(group = "org.pf4j", name = "pf4j-update", version = "2.3.0")
//    implementation(group = "net.runelite", name = "rlawt", version = "1.4")
//    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")
//    implementation(group = "org.apache.commons", name = "commons-text", version = "1.9")
//
//    implementation(platform("org.lwjgl:lwjgl-bom:3.3.2"))
//    implementation(group = "org.lwjgl", name = "lwjgl")
//    implementation(group = "org.lwjgl", name = "lwjgl-opengl")
//    implementation(group = "org.lwjgl", name = "lwjgl-opencl")

    implementation(project(":runelite-api"))
    implementation(project(":cache"))
    implementation(project(":http-api"))

    implementation(project(":runelite-client"))
}


tasks {
    named<JavaExec>("run") {
        classpath = project(":runelite-client").sourceSets.main.get().runtimeClasspath.plus(project.sourceSets.main.get().runtimeClasspath)
        enableAssertions = true
        jvmArgs("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
        args(listOf("--cached-uuid", "--cached-random-dat", "--debug", "--developer-mode"))
        mainClass.set("net.runelite.devkit.Launcher")
    }

    jar {
        manifest {
            attributes(mutableMapOf("Main-Class" to "net.runelite.devkit.Launcher"))
        }
    }


    shadowJar {
        archiveClassifier.set("shaded")
    }

}
