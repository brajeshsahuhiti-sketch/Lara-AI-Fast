@echo off
set JAR=%~dp0gradle\wrapper\gradle-wrapper.jar
if exist "%JAR%" (
  java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
echo gradle-wrapper.jar missing. Run: gradle wrapper --gradle-version 8.9  (or open in Android Studio)
exit /b 1
