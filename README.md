# PagoPA Help desk Commands Service

## Overview
`pagopa-ecommerce-helpdesk-commands-service` is a Kotlin-based microservice designed to support manual operations, such as refunds, for transactions related to the pagoPA ecommerce platform. 
This service leverages Kotlin's native compilation to achieve optimal performance and efficiency.

## Technology Stack

- Kotlin
- Spring Boot (native)

### Environment variables

These are all environment variables needed by the application:

| Variable name                                 | Description                                                                                                                                                                     | type    | default    |
|-----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|------------|
| NPG_URI                                       | NPG service URI                                                                                                                                                                 | string  |            |
| NPG_READ_TIMEOUT                              | NPG service HTTP read timeout                                                                                                                                                   | integer |            |
| NPG_CONNECTION_TIMEOUT                        | NPG service HTTP connection timeout                                                                                                                                             | integer |            |
| NPG_CARDS_PSP_KEYS                            | Secret structure that holds psp - api keys association for authorization request                                                                                                | string  |            |
| NPG_CARDS_PSP_LIST                            | List of all psp ids that are expected to be found into the NPG_CARDS_PSP_KEYS configuration (used for configuration cross validation                                            | string  |            |
| NPG_PAYPAL_PSP_KEYS                           | Secret structure that holds psp - api keys association for authorization request used for APM PAYPAL payment method                                                             | string  |            |
| NPG_PAYPAL_PSP_LIST                           | List of all psp ids that are expected to be found into the NPG_PAYPAL_PSP_KEYS configuration (used for configuration cross validation)                                          | string  |            |
| NPG_BANCOMATPAY_PSP_KEYS                      | Secret structure that holds psp - api keys association for authorization request used for APM Bancomat pay payment method                                                       | string  |            |
| NPG_BANCOMATPAY_PSP_LIST                      | List of all psp ids that are expected to be found into the NPG_BANCOMATPAY_PSP_KEYS configuration (used for configuration cross validation)                                     | string  |            |
| NPG_MYBANK_PSP_KEYS                           | Secret structure that holds psp - api keys association for authorization request used for APM My bank payment method                                                            | string  |            |
| NPG_MYBANK_PSP_LIST                           | List of all psp ids that are expected to be found into the NPG_MYBANK_PSP_KEYS configuration (used for configuration cross validation)                                          | string  |            |
| NPG_SATISPAY_PSP_KEYS                         | Secret structure that holds psp - api keys association for authorization request used for APM Satispay payment method                                                           | string  |            |
| NPG_SATISPAY_PSP_LIST                         | List of all psp ids that are expected to be found into the NPG_SATISPAY_PSP_KEYS configuration (used for configuration cross validation)                                        | string  |            |
| NPG_APPLEPAY_PSP_KEYS                         | Secret structure that holds psp - api keys association for authorization request used for APM Apple pay payment method                                                          | string  |            |
| NPG_APPLEPAY_PSP_LIST                         | List of all psp ids that are expected to be found into the NPG_APPLEPAY_PSP_KEYS configuration (used for configuration cross validation)                                        | string  |            |
| REDIRECT_PAYMENT_TYPE_CODES                   | List of all redirect payment type codes that are expected to be present in other redirect configurations such as REDIRECT_URL_MAPPING (used for configuration cross validation) | string  |            |
| REDIRECT_URL_MAPPING                          | Key-value string map PSP to backend URI mapping that will be used for Redirect payments                                                                                         | string  |            |
| NODE_FORWARDER_URL                            | Node forwarder backend URL                                                                                                                                                      | string  |            |
| NODE_FORWARDER_READ_TIMEOUT                   | Node forwarder HTTP api call read timeout in milliseconds                                                                                                                       | integer |            |
| NODE_FORWARDER_CONNECTION_TIMEOUT             | Node forwarder HTTP api call connection timeout in milliseconds                                                                                                                 | integer |            |
| NODE_FORWARDER_API_KEY                        | Node forwarder api key                                                                                                                                                          | string  |            |
| NPG_GOOGLE_PAY_PSP_KEYS                       | Secret structure that holds psp - api keys association for authorization request used for APM Google pay payment method                                                         | string  |            |
| NPG_GOOGLE_PAY_PSP_LIST                       | List of all psp ids that are expected to be found into the NPG_GOOGLE_PAY_PSP_KEYS configuration (used for configuration cross validation)                                      | string  |            |
| AZURE_QUEUE_NATIVE_CLIENT_ENABLED             | Flag to choose if we have to use the azure SDK storage queue client or the Rest API client                                                                                      | string  |            |
| ECOMMERCE_STORAGE_TRANSIENT_CONNECTION_STRING | Azure Storage connection string for transient storage queues                                                                                                                    | string  |            |
| TRANSACTION_REFUND_QUEUE_NAME                 | Name of the Azure Storage queue for transaction refund events                                                                                                                   | string  |            |
| TRANSACTION_NOTIFICATIONS_QUEUE_NAME          | Name of the Azure Storage queue for transaction notification events                                                                                                             | string  |            |
| MONGO_REPLICA_SET_OPTION                      | The replica set connection string option valued with the name of the replica set. See docs *                                                                                    | string  |            |
| NPG_TCP_KEEP_ALIVE_ENABLED                    | Enable TCP keep alive for NPG connections                                                                                                                                       | boolean | true       |
| NPG_TCP_KEEP_ALIVE_IDLE                       | TCP keep alive idle time in seconds for NPG connections                                                                                                                         | integer | 300        |
| NPG_TCP_KEEP_ALIVE_INTVL                      | TCP keep alive interval in seconds for NPG connections                                                                                                                          | integer | 60         |
| NPG_TCP_KEEP_ALIVE_CNT                        | TCP keep alive count for NPG connections                                                                                                                                        | integer | 8          |
| TRANSIENT_QUEUES_TTL_SECONDS                  | Time to live in seconds for transient queues                                                                                                                                    | integer | 7200       |
| DEFAULT_LOGGING_LEVEL                         | Default logging level for the application                                                                                                                                       | string  | info       |
| APP_LOGGING_LEVEL                             | Application specific logging level                                                                                                                                              | string  | debug      |
| MONGO_HOST                                    | MongoDB host address                                                                                                                                                            | string  | mongodb    |
| MONGO_PORT                                    | MongoDB port number                                                                                                                                                             | integer | 27017      |
| MONGO_USERNAME                                | MongoDB username for authentication                                                                                                                                             | string  | admin      |
| MONGO_PASSWORD                                | MongoDB password for authentication                                                                                                                                             | string  |            |
| MONGO_SSL_ENABLED                             | Enable SSL for MongoDB connections                                                                                                                                              | boolean | false      |
| MONGO_DB_NAME                                 | MongoDB database name                                                                                                                                                           | string  | eventstore |
| MONGO_MIN_POOL_SIZE                           | Minimum connection pool size for MongoDB                                                                                                                                        | integer | 0          |
| MONGO_MAX_POOL_SIZE                           | Maximum connection pool size for MongoDB                                                                                                                                        | integer | 20         |
| MONGO_MAX_IDLE_TIMEOUT_MS                     | Maximum idle timeout in milliseconds for MongoDB connections                                                                                                                    | integer | 60000      |
| MONGO_CONNECTION_TIMEOUT_MS                   | Connection timeout in milliseconds for MongoDB                                                                                                                                  | integer | 1000       |
| MONGO_SOCKET_TIMEOUT_MS                       | Socket timeout in milliseconds for MongoDB                                                                                                                                      | integer | 10000      |
| MONGO_SERVER_SELECTION_TIMEOUT_MS             | Server selection timeout in milliseconds for MongoDB                                                                                                                            | integer | 2000       |
| MONGO_WAITING_QUEUE_MS                        | Waiting queue timeout in milliseconds for MongoDB                                                                                                                               | integer | 2000       |
| MONGO_HEARTBEAT_FREQUENCY_MS                  | Heartbeat frequency in milliseconds for MongoDB                                                                                                                                 | integer | 5000       |

An example configuration of these environment variables is in the `.env.example` file.

It is recommended to create a new .env file by copying the example one, using the following command (make sure you are in the .env.example folder):

```shell
cp .env.example .env
```

## Working with Windows

If you are developing on Windows, it is recommended the use of WSL2 combined with IntelliJ IDEA.

The IDE should be installed on Windows, with the repository cloned into a folder in WSL2. All the necessary tools will be installed in the Linux distro of your choice.

You can find more info on how to set up the environment following the link below.

https://www.jetbrains.com/help/idea/how-to-use-wsl-development-environment-in-product.html

After setting up the WSL environment, you can test the application by building it through either Spring Boot or Docker.

## Spring Boot Native
### Requirements
1. You must use GraalVM Java SDK to build native executable locally.
   https://www.graalvm.org/downloads/. It is recommended to use SDKMAN
2. You must use GraalVM gradle plugin which allows to configure a lot of setting for native compilation, like automatic toolchain detection https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html

If you're experiencing issue with GraalVM not found like errors, be sure to use GraalVM for the project and try to enable automatic toolchain detection.
Also, you can use [SDKMAN](https://sdkman.io/install) to provide a better JVM env "switching".

### Install eCommerce commons library locally

There is an `installLibs` task in the Gradle build file that takes care of properly fetching and
building `ecommerce-commons`. It does so by executing a shell script that performs a repository clone, checks out to the version set in the
build file, and builds the library with Maven using Java 17.

If you want to build the `ecommerce-commons` library, you can run the build command with `-PbuildCommons`:

```Shell
$ ./gradlew build -PbuildCommons
```

Alternatively, you can run the installation task directly:

```Shell
$ ./gradlew installLibs -PbuildCommons
```

#### Configuration Properties

These two properties in `build.gradle.kts` control the `ecommerce-commons` version and git reference:

```kotlin
val ecommerceCommonsVersion = "x.y.z" // ecommerce commons wanted pom version
val ecommerceCommonsGitRef = ecommerceCommonsVersion // the branch/tag to be checked out
```

`ecommerceCommonsGitRef` has by default the same value as `ecommerceCommonsVersion`, so the version tagged
with `"x.y.z"` will be checked out and installed locally.

This value was left as a separate property because, during development phases, it can be changed to a feature branch,
making the local build use a ref branch other than a tag for development purposes.

#### Installation Process

The installation is handled by `pagopa-ecommerce-commons-maven-install.sh` which:

1. Clones the ecommerce-commons repository
2. Checks out the specified version/branch
3. Detects and uses Java 17 for building (required for commons compatibility)
4. Runs `mvn install -DskipTests` to install the library to local Maven repository
5. Cleans up temporary files

#### Utility Tasks

- **Print current commons version**: `./gradlew printCommonsVersion -q`
- **Install commons only**: `./gradlew installLibs -PbuildCommons`

#### Java Version Requirements

- **eCommerce Commons**: Requires Java 17 for building
- **Main Application**: Uses Java 21 for GraalVM native compilation

The installation script automatically detects Java 17 from common locations or uses `JAVA_HOME_17` environment variable if set.

#### Docker Build Integration

The Docker build uses a multi-stage approach:
1. **Commons stage**: Uses OpenJDK 17 to build and install ecommerce-commons
2. **Main stage**: Uses GraalVM 21 to compile the application natively

Running `docker compose up` automatically handles the commons installation without requiring manual intervention.

You also need one of:
 - an azurite instance running locally
 - use DEV values

According to the choice, please adjust accordingly:
 - `ECOMMERCE_STORAGE_TRANSIENT_CONNECTION_STRING`
 - `TRANSACTION_REFUND_QUEUE_NAME`
 - `TRANSACTION_NOTIFICATIONS_QUEUE_NAME`

#### Compile & Run
To compile microservice to native executable you can use the following gradle task:
```shell
gradle :nativeCompile
```
This will produce an executable inside `build/native/nativeCompile/`

N.B. Compiling into a native executable takes a long time. Locally, it is recommended to launch it normally (in java) in order to test the service.

Also exist a gradle command to compile and run it directly:
```shell
gradle :nativeRun
```

If you want to run the project locally with Spring Boot, you should initialize npg-mock and psp-mock using the following commands (you need yarn installed on the WSL):

```shell
yarn add json-server@0.17.4 --exact
```

The following command should be used to start the mock server for local testing

```shell
yarn json-server ./npg-server.json --routes ./routes.json --middlewares ./middleware.js --host=0.0.0.0
yarn json-server ./psp-server.json --routes ./routes.json --middlewares ./middleware.js --host=0.0.0.0
```

#### Test Coverage
To generate test coverage reports, run:
```shell
gradle jacocoTestReport
```
The coverage report will be generated at `build/reports/jacoco/test/html/index.html`. Open this file in a browser to view detailed coverage metrics.

## Docker

The project can be built and run using Docker and docker-compose. You should install Docker Desktop on Windows and go through its settings to set up the WSL integration.

You can find more info at the following link: https://docs.docker.com/desktop/wsl/

After setting up Docker, you can use the command:

```shell
docker-compose up
```

The docker-compose up command will build the image and start the containers.

## Integration Testing

This service supports two integration testing approaches:

### Local Integration Testing (Docker Compose)
Run integration tests using the local Docker Compose setup:

1. Start the local environment:
   ```shell
   docker-compose up
   ```

2. Run the Postman collection:
   ```shell
   newman run api-tests/v1/helpdeskcommands.api.tests.local.json --environment=api-tests/env/helpdeskcommands_local.env.json
   ```

### eCommerce-Local Integration Testing
The service is integrated into the [pagopa-ecommerce-local](https://github.com/pagopa/pagopa-ecommerce-local) repository for comprehensive platform testing.

**Pipeline Integration**: The CI/CD pipeline includes an `IntegrationTestEcommerceLocal` stage that:
- Dynamically sets the service branch using `ECOMMERCE_HELPDESK_COMMANDS_COMMIT_SHA` 
- Extracts the ecommerce-commons version dynamically using `./gradlew -q printCommonsVersion`
- Runs tests in the full ecommerce environment

**Polling Tests**: Integration tests include transaction state polling to verify:
- Refund operations: Transaction state transitions through Azure Storage Queues and Event Dispatcher
- Email resend operations: Notification request processing via queue-based messaging

**Dependencies**: The service depends on:
- `storage` (Azurite) - for Azure Storage Queue emulation
- `mongo` - for transaction state persistence  
- `pagopa-npg-mock`, `pagopa-psp-mock` - for payment gateway mocking
- `pagopa-ecommerce-event-dispatcher-service` - for queue message processing

#### Tips
The main issue with native image is related to Java Reflection.
GraalVM produces a metadata files containing reflection data.  There is also a repository containing the metadata of some of the most widely used external libraries. You can include this metadata via the gradle plugin
```gradle
graalvmNative {
    metadataRepository {
        enabled.set(true)
        version.set("0.2.6")
    }
}
```

Spring using AOT try automatically to do the best, but you can also find issues.
Here https://docs.spring.io/spring-framework/reference/core/aot.html#aot.hints you can find a lot of tips, like `@RegisterReflectionForBinding` 

#### Dependency lock

This feature use the content of `gradle.lockfile` to check the declared dependencies against the locked one.

If a transitive dependencies have been upgraded the build will fail because of the locked version mismatch.

The following command can be used to upgrade dependency lockfile:

```shell
./gradlew dependencies --write-locks 
```

Running the above command will cause the `gradle.lockfile` to be updated against the current project dependency
configuration

#### Dependency verification

This feature is enabled by adding the gradle `./gradle/verification-metadata.xml` configuration file.

Perform checksum comparison against dependency artifact (jar files, zip, ...) and metadata (pom.xml, gradle module
metadata, ...) used during build
and the ones stored into `verification-metadata.xml` file raising error during build in case of mismatch.

The following command can be used to recalculate dependency checksum:

```shell
./gradlew --write-verification-metadata sha256 clean spotlessApply build --no-build-cache --refresh-dependencies
```

In the above command the `clean`, `spotlessApply` `build` tasks where chosen to be run
in order to discover all transitive dependencies used during build and also the ones used during
spotless apply task used to format source code.

The above command will upgrade the `verification-metadata.xml` adding all the newly discovered dependencies' checksum.
Those checksum should be checked against a trusted source to check for corrispondence with the library author published
checksum.

`/gradlew --write-verification-metadata sha256` command appends all new dependencies to the verification files but does
not remove
entries for unused dependencies.

This can make this file grow every time a dependency is upgraded.

To detect and remove old dependencies make the following steps:

1. Delete, if present, the `gradle/verification-metadata.dryrun.xml`
2. Run the gradle write-verification-metadata in dry-mode (this will generate a verification-metadata-dryrun.xml file
   leaving untouched the original verification file)
3. Compare the verification-metadata file and the verification-metadata.dryrun one checking for differences and removing
   old unused dependencies

The 1-2 steps can be performed with the following commands

```Shell
rm -f ./gradle/verification-metadata.dryrun.xml 
./gradlew --write-verification-metadata sha256 clean spotlessApply build --dry-run
```

The resulting `verification-metadata.xml` modifications must be reviewed carefully checking the generated
dependencies checksum against official websites or other secure sources.

If a dependency is not discovered during the above command execution it will lead to build errors.

You can add those dependencies manually by modifying the `verification-metadata.xml`
file adding the following component:

```xml

<verification-metadata>
    <!-- other configurations... -->
    <components>
        <!-- other components -->
        <component group="GROUP_ID" name="ARTIFACT_ID" version="VERSION">
            <artifact name="artifact-full-name.jar">
                <sha256 value="sha value"
                        origin="Description of the source of the checksum value"/>
            </artifact>
            <artifact name="artifact-pom-file.pom">
                <sha256 value="sha value"
                        origin="Description of the source of the checksum value"/>
            </artifact>
        </component>
    </components>
</verification-metadata>
```

Add those components at the end of the components list and then run the

```shell
./gradlew --write-verification-metadata sha256 clean spotlessApply build --no-build-cache --refresh-dependencies
```

that will reorder the file with the added dependencies checksum in the expected order.

Finally, you can add new dependencies both to gradle.lockfile writing verification metadata running

```shell
 ./gradlew dependencies --write-locks --write-verification-metadata sha256 --no-build-cache --refresh-dependencies
```

For more information read the
following [article](https://docs.gradle.org/8.1/userguide/dependency_verification.html#sec:checksum-verification)

## Contributors 👥

Made with ❤️ by PagoPA S.p.A.

### Maintainers

See `CODEOWNERS` file
