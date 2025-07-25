#!/bin/sh
set -e

version=$1
checkoutFolder=checkouts
gitRepo=https://github.com/pagopa/pagopa-ecommerce-commons
rm -rf $checkoutFolder
mkdir $checkoutFolder

detect_and_setup_java21() {
    # Method 1: Check if JAVA_HOME is already set to Java 21
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            echo "Java 21 already configured via JAVA_HOME: $JAVA_HOME"
            return 0
        fi
    fi
    
    # Method 2: Check system java
    if command -v java >/dev/null 2>&1; then
        SYSTEM_JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$SYSTEM_JAVA_VERSION" = "21" ]; then
            echo "System Java 21 detected"
            return 0
        fi
    fi
    
    # Method 3: Check known Java 21 installation paths
    for java_path in \
        "/usr/lib/jvm/temurin-21-jdk-amd64/bin/java" \
        "/usr/lib/jvm/java-21-amazon-corretto/bin/java" \
        "/usr/lib/jvm/java-21-openjdk/bin/java" \
        "/usr/lib/jvm/java-21-openjdk-amd64/bin/java" \
        "/usr/lib/jvm/temurin-21-jdk/bin/java" \
        "/opt/java/openjdk-21/bin/java" \
        "/usr/lib/jvm/default-jvm/bin/java" \
        "/opt/java/openjdk/bin/java"; do
        
        if [ -x "$java_path" ]; then
            JAVA_VERSION=$("$java_path" -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1 2>/dev/null || echo "unknown")
            if [ "$JAVA_VERSION" = "21" ]; then
                export JAVA_HOME=$(dirname $(dirname "$java_path"))
                export PATH="$JAVA_HOME/bin:$PATH"
                echo "Java 21 found at: $JAVA_HOME"
                return 0
            fi
        fi
    done
    
    echo "ERROR: Java 21 not found in any known location."
    echo "Current environment: $(uname -a)"
    echo "Current JAVA_HOME: ${JAVA_HOME:-not set}"
    echo "Available Java versions:"
    if command -v java >/dev/null 2>&1; then
        java -version 2>&1 | head -n3
    else
        echo "   No Java found in PATH"
    fi
    
    echo ""
    echo "To fix this issue:"
    echo "   1. Install Java 21 on your system"
    echo "   2. Set JAVA_HOME to point to Java 21"
    echo "   3. Ensure Java 21 is in your PATH"
    echo ""
    echo "CI/CD environments should configure Java 21 before running this script."
    
    return 1
}

if ! detect_and_setup_java21; then
    echo "Failed to setup Java 21 environment"
    exit 1
fi

java -version

cd $checkoutFolder

echo "Cloning ecommerce commons repo... $gitRepo"
git clone $gitRepo
cd pagopa-ecommerce-commons
echo "Checking out ecommerce common ref $version"
git checkout $version

./mvnw install -DskipTests

cd ../../
rm -rf $checkoutFolder
echo "Successfully installed ecommerce commons"