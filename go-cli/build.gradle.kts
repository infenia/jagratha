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
plugins {
  id("base")
}

// Build goBuild task
tasks.register("goBuild") {
  group = "build"
  description = "Build Go CLI binary for current OS and architecture"

  notCompatibleWithConfigurationCache("Go build process requires external command execution")

  doLast {
    file("build").mkdirs()

    val osName = System.getProperty("os.name").lowercase()
    val goos = when {
      osName.contains("mac") || osName.contains("darwin") -> "darwin"
      osName.contains("windows") -> "windows"
      else -> "linux"
    }

    val osArch = System.getProperty("os.arch").lowercase()
    val goarch = when {
      osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
      else -> "amd64"
    }

    val baseName = "yukta-$goos-$goarch"
    val binaryName = if (goos == "windows") "$baseName.exe" else baseName

    println("Building Go CLI binary for $goos/$goarch -> build/$binaryName")

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

    println("Successfully built Go CLI binary: build/$binaryName")
  }
}

// Build goBuildAll task
tasks.register("goBuildAll") {
  group = "build"
  description = "Build Go CLI binaries for all supported platforms"

  notCompatibleWithConfigurationCache("Go build process requires external command execution")

  doLast {
    val platforms = listOf(
      Pair("linux", "amd64"),
      Pair("linux", "arm64"),
      Pair("darwin", "amd64"),
      Pair("darwin", "arm64"),
      Pair("windows", "amd64")
    )

    file("build").mkdirs()

    for ((goos, goarch) in platforms) {
      val baseName = "yukta-$goos-$goarch"
      val binaryName = if (goos == "windows") "$baseName.exe" else baseName

      println("Building Go CLI for $goos/$goarch -> build/$binaryName")

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

    println("Successfully built Go CLI binaries for all platforms")
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
