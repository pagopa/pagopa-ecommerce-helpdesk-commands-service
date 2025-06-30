FROM amazoncorretto:21-alpine@sha256:6a98c4402708fe8d16e946b4b5bac396379ec5104c1661e2a27b2b45cf9e2d16 AS commons-builder

WORKDIR /workspace/app
RUN microdnf install -y findutils git

ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH=$JAVA_HOME/bin:$PATH

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
