FROM openjdk:17-jdk@sha256:528707081fdb9562eb819128a9f85ae7fe000e2fbaeaf9f87662e7b3f38cb7d8 AS commons-builder

WORKDIR /workspace/app
RUN microdnf install -y findutils git
COPY . .
RUN chmod +x ./gradlew
RUN chmod +x ./pagopa-ecommerce-commons-maven-install.sh
RUN ./gradlew installLibs -PbuildCommons

FROM ghcr.io/graalvm/native-image-community:21.0.2@sha256:faed0fd6809b138254bdd6c7046e56894f4d9566ecbc7b0952aab43e65e16e0e AS builder
WORKDIR /workspace/app
RUN microdnf install -y findutils git

COPY . .
COPY --from=commons-builder /root/.m2 /root/.m2

RUN chmod +x ./gradlew
RUN ./gradlew :nativeCompile

FROM debian:stable-20240701-slim@sha256:f8bbfa052db81e5b8ac12e4a1d8310a85d1509d4d0d5579148059c0e8b717d4e
WORKDIR /app/

EXPOSE 8080

COPY --from=builder /workspace/app/build/native/nativeCompile/pagopa-helpdesk-commands-service .

ENTRYPOINT ["./pagopa-helpdesk-commands-service"]
