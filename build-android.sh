#!/bin/bash

# Script to fix and build Android project

echo "🔧 Setting up Android project..."

# Create gradle wrapper directory if it doesn't exist
mkdir -p gradle/wrapper

# Download gradle-wrapper.jar if missing
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    echo "📥 Downloading gradle-wrapper.jar..."
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        "https://services.gradle.org/distributions/gradle-8.2-bin.zip" || \
    wget -O gradle/wrapper/gradle-wrapper.jar \
        "https://services.gradle.org/distributions/gradle-8.2-bin.zip" || \
    echo "❌ Failed to download gradle wrapper"
fi

# Make gradlew executable
chmod +x ./gradlew

echo "📋 Project structure:"
ls -la gradle/wrapper/
ls -la ./gradlew

echo "🔨 Building project..."
./gradlew assembleDebug --stacktrace

echo "✅ Build complete!"