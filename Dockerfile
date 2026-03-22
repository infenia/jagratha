# Copyright 2026 Infenia Private Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Stage 1: Build native image with GraalVM
FROM ghcr.io/graalvm/native-image:25-muslib AS builder

WORKDIR /build
COPY . .

# Build native image with aggressive optimizations
RUN ./gradlew nativeCompile -x test --no-daemon

# Stage 2: Compress executable with UPX (optional but recommended)
FROM ubuntu:24.04 AS compressor

RUN apt-get update && apt-get install -y upx && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/boot/build/native/nativeCompile/yukta /tmp/yukta

# Attempt UPX compression with fallback (some binaries cannot be compressed)
RUN upx --best --lzma -o /tmp/yukta.compressed /tmp/yukta 2>/dev/null || cp /tmp/yukta /tmp/yukta.compressed

# Stage 3: Final production image with distroless base (minimal footprint)
FROM gcr.io/distroless/cc-debian12:nonroot

# Distroless images: no shell, no package manager, no unnecessary files
# Only libc6 + dynamic loader + application binary

COPY --from=compressor /tmp/yukta.compressed /app/yukta

# Application will listen on 8080
EXPOSE 8080

# Nonroot user: runs as unprivileged user (UID 65532)
# Set HOME and PATH for application
ENV HOME=/home/nonroot \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Health check (distroless has no curl, use exec probe instead)
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD ["/app/yukta", "--help"]

ENTRYPOINT ["/app/yukta"]
