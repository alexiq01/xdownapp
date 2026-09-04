#!/bin/bash
# XDown - Setup Script
# Run this script to set up the Gradle wrapper

set -e

echo "Setting up XDown project..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Java is not installed. Please install JDK 17+"
    echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  macOS: brew install openjdk@17"
    exit 1
fi

echo "Java found: $(java -version 2>&1 | head -n 1)"

# Check if gradle is installed
if command -v gradle &> /dev/null; then
    echo "Gradle found, generating wrapper..."
    gradle wrapper --gradle-version 8.5
else
    echo "Gradle not found. Downloading wrapper manually..."
    
    WRAPPER_DIR="gradle/wrapper"
    JAR_URL="https://services.gradle.org/distributions/gradle-8.5-bin.zip"
    WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
    
    if [ ! -f "$WRAPPER_JAR" ]; then
        echo "Downloading gradle-wrapper.jar..."
        # Create a minimal gradle wrapper jar using gradle init
        if command -v gradle &> /dev/null; then
            gradle wrapper
        else
            echo "Please install Gradle or download gradle-wrapper.jar manually"
            echo "Place it in: $WRAPPER_DIR/gradle-wrapper.jar"
            echo "Download from: https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
        fi
    fi
fi

echo ""
echo "Setup complete!"
echo ""
echo "To build the project:"
echo "  ./gradlew assembleDebug"
echo ""
echo "To install on device:"
echo "  ./gradlew installDebug"
echo ""
echo "Or open the project in Android Studio"
