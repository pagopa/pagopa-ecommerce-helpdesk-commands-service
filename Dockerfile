FROM ghcr.io/graalvm/native-image-community:21.0.2@sha256:faed0fd6809b138254bdd6c7046e56894f4d9566ecbc7b0952aab43e65e16e0e AS builder
WORKDIR /workspace/app
RUN microdnf install -y git unzip findutils && microdnf clean all

COPY . .

RUN --mount=type=secret,id=GITHUB_TOKEN \
    GITHUB_TOKEN=$(cat /run/secrets/GITHUB_TOKEN) \
    ./gradlew dependencies --no-daemon

RUN --mount=type=secret,id=GITHUB_TOKEN \
    GITHUB_TOKEN=$(cat /run/secrets/GITHUB_TOKEN) \
    ./gradlew :nativeCompile \
              --no-daemon \
              -Pgraalvm.native.imageArgs="-J-Xmx6g \
                --strict-image-heap -H:+AddAllCharsets"

FROM debian:stable-20240701-slim AS runtime
WORKDIR /app
EXPOSE 8080
COPY --from=builder /workspace/app/build/native/nativeCompile/pagopa-helpdesk-commands-service .
ENTRYPOINT ["./pagopa-helpdesk-commands-service"]