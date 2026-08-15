FROM --platform=linux/amd64 eclipse-temurin:17-jdk

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip \
 && rm -rf /var/lib/apt/lists/* \
 && curl -sSLo /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
 && mkdir -p $ANDROID_HOME/cmdline-tools \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platforms;android-36" "build-tools;36.0.0" > /dev/null

WORKDIR /src
COPY . .

RUN ./gradlew --no-daemon assembleArm64Debug assembleArmv7Debug

CMD ["sh", "-c", "cp /src/app/build/outputs/apk/*/debug/*.apk /out/ && ls -1 /out"]
