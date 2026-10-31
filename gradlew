#!/bin/sh
# Launcher stub: needs gradle/wrapper/gradle-wrapper.jar (binary, not included - see README).
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then
  exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
echo "gradle-wrapper.jar missing. Fix: install Gradle 8.9+ and run 'gradle wrapper --gradle-version 8.9', or just open the project in Android Studio." >&2
exit 1
