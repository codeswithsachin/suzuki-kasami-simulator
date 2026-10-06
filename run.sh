#!/bin/sh
# Compile and run the Suzuki-Kasami simulator (needs a JDK 17+)
cd "$(dirname "$0")"
mkdir -p out
javac -d out $(find src -name "*.java") && java -cp out skm.Main
