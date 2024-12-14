#!/bin/sh
# Set default repository URL
DEFAULT_REPO="https://github.com/ge0rg/aprsdroid.git"

# Use the first argument as the repository URL, or default if not provided
REPO_URL="${1:-$DEFAULT_REPO}"

# Move to the working directory
cd /home/user || exit 1

# Print the current user
whoami

# Clone the repository
echo "Cloning repository: $REPO_URL"
git clone "$REPO_URL" aprsdroid

# Change to the cloned directory
cd aprsdroid/ || exit 1

# Update git submodules
git submodule update --init --recursive

# Set the local.properties file
echo "mapsApiKey=a" > local.properties

# Remove specific lines from build.gradle using sed
sed -i '/id "app.brant.amazonappstorepublisher" version "0.1.0"/d' build.gradle
sed -i '/amazon {/,/}/d' build.gradle

# Run the Gradle assemble task
./gradlew assemble

ls -lathr

# Copy the APK to the mounted volume
cp /home/user/aprsdroid/build/outputs/apk/release/aprsdroid-release-unsigned.apk /home/user/output/aprsdroid-release.apk
