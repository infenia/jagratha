/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.gradle.api.GradleException

plugins {
  id("base")
}

// Helper function to detect current OS
fun getHostOS(): String {
  val osName = System.getProperty("os.name").lowercase()
  return when {
    osName.contains("mac") || osName.contains("darwin") -> "darwin"
    osName.contains("windows") -> "windows"
    else -> "linux"
  }
}

// Helper function to detect current architecture
fun getHostArch(): String {
  val osArch = System.getProperty("os.arch").lowercase()
  return when {
    osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
    else -> "amd64"
  }
}

// Helper function to get binary name
fun getBinaryName(goos: String, goarch: String): String {
  val baseName = "yukta-$goos-$goarch"
  return if (goos == "windows") "$baseName.exe" else baseName
}

// Build for current OS/architecture
tasks.register("goBuild") {
  group = "build"
  description = "Build Go CLI binary for current OS and architecture"

  doLast {
    file("build").mkdirs()

    val goos = getHostOS()
    val goarch = getHostArch()
    val binaryName = getBinaryName(goos, goarch)

    logger.info("Building Go CLI binary for $goos/$goarch -> build/$binaryName")

    val processBuilder = ProcessBuilder(
      "go", "build", "-ldflags=-s -w", "-o", "build/$binaryName", "./cmd/yukta"
    )
    processBuilder.directory(projectDir)
    processBuilder.environment()["GOOS"] = goos
    processBuilder.environment()["GOARCH"] = goarch
    processBuilder.environment()["CGO_ENABLED"] = "0"

    val process = processBuilder.start()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
      val errorOutput = process.errorStream.bufferedReader().readText()
      throw GradleException("Go build failed for $goos/$goarch with exit code $exitCode\n$errorOutput")
    }

    logger.info("Successfully built Go CLI binary: build/$binaryName")
  }
}

// Build for all supported platforms
tasks.register("goBuildAll") {
  group = "build"
  description = "Build Go CLI binaries for all supported platforms"

  doLast {
    val platforms = listOf(
      Pair("linux", "amd64"),
      Pair("linux", "arm64"),
      Pair("darwin", "amd64"),
      Pair("darwin", "arm64"),
      Pair("windows", "amd64")
    )

    // Ensure build directory exists
    file("build").mkdirs()

    for ((goos, goarch) in platforms) {
      val binaryName = getBinaryName(goos, goarch)
      logger.info("Building Go CLI for $goos/$goarch -> build/$binaryName")

      val processBuilder = ProcessBuilder(
        "go", "build", "-ldflags=-s -w", "-o", "build/$binaryName", "./cmd/yukta"
      )
      processBuilder.directory(projectDir)
      processBuilder.environment()["GOOS"] = goos
      processBuilder.environment()["GOARCH"] = goarch
      processBuilder.environment()["CGO_ENABLED"] = "0"

      val exitCode = processBuilder.start().waitFor()
      if (exitCode != 0) {
        throw GradleException("Go build failed for $goos/$goarch with exit code $exitCode")
      }
    }

    logger.info("Successfully built Go CLI binaries for all platforms")
  }
}

// Clean build artifacts
tasks.register<Delete>("goClean") {
  group = "build"
  description = "Clean Go CLI build artifacts"

  delete("build")
  delete("bin")
}

// Wire tasks to standard Gradle lifecycle
tasks.named("assemble") {
  dependsOn("goBuild")
}

tasks.named("clean") {
  dependsOn("goClean")
}
