# Base image
FROM ubuntu:20.04

# Set environment variables to ensure non-interactive mode and set timezone
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC

# Install required dependencies
RUN apt-get update && apt-get install -y \
    git \
    openjdk-8-jdk \
    wget \
    unzip \
    vim-nox \
    tzdata && \
    ln -fs /usr/share/zoneinfo/$TZ /etc/localtime && \
    dpkg-reconfigure --frontend noninteractive tzdata && \
    apt-get clean

# Set up environment variables
ENV ANDROID_SDK_ROOT=/home/user/android

# Install Android SDK
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget "https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip" -O /tmp/cmdline-tools.zip && \
    unzip /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    rm /tmp/cmdline-tools.zip && \
    mkdir ${ANDROID_SDK_ROOT}/licenses && \
    echo 24333f8a63b6825ea9c5514f83c2829b004d1fee > ${ANDROID_SDK_ROOT}/licenses/android-sdk-license && \
    echo 84831b9409646a918e30573bab4c9c91346d8abd > ${ANDROID_SDK_ROOT}/licenses/android-sdk-preview-license && \
    yes | ${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin/sdkmanager --install 'emulator' 'system-images;android-24;default;armeabi-v7a'

# Set PATH
ENV PATH=${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator:${PATH}

# Create a non-root user
RUN useradd -m user

# Copy the entrypoint script into the Docker image
COPY entrypoint.sh /home/user/entrypoint.sh

# Make the entrypoint script executable
RUN chmod +x /home/user/entrypoint.sh && chown -R user:user /home/user/

# Set the working directory and change to the non-root user
WORKDIR /home/user
USER user

# Set the entrypoint
ENTRYPOINT ["/home/user/entrypoint.sh"]
