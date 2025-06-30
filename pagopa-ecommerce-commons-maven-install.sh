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

./mvnw install -DskipTests

cd ../../
rm -rf $checkoutFolder
echo "Successfully installed ecommerce commons"