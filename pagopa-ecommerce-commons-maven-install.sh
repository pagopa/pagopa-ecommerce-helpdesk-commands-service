#!/bin/sh
set -e

version=$1
checkoutFolder=checkouts
gitRepo=https://github.com/pagopa/pagopa-ecommerce-commons
rm -rf $checkoutFolder
mkdir $checkoutFolder

cd $checkoutFolder

echo "Cloning ecommerce commons repo... $gitRepo"
git clone $gitRepo
cd pagopa-ecommerce-commons
echo "Checking out ecommerce common ref $version"
git checkout $version

if [ -n "$JAVA_HOME_17" ]; then
    export JAVA_HOME=$JAVA_HOME_17
    echo "Using JAVA_HOME_17: $JAVA_HOME"
elif command -v /usr/lib/jvm/java-17-openjdk/bin/java >/dev/null 2>&1; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
elif [ -x "/usr/libexec/java_home" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null) || true
    if [ -n "$JAVA_HOME" ]; then
        export JAVA_HOME
        echo "Using Java 17 from java_home: $JAVA_HOME"
    fi
fi

if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: Java 17 not found. Please install Java 17 or set JAVA_HOME_17"
    echo "Current JAVA_HOME: $JAVA_HOME"
    exit 1
fi

echo "Using Java version:"
$JAVA_HOME/bin/java -version

REPO_PATH="/home/vsts/work/1/s/.m2/repository"
#REPO_PATH="/home/vsts/.m2/repository"
JAVA_HOME=$JAVA_HOME ./mvnw install -DskipTests -Dmaven.repo.local=$REPO_PATH

cd ../../
rm -rf $checkoutFolder
echo "Successfully installed ecommerce commons"