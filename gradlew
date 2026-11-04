#!/bin/sh
# Gradle Start Up Script for UN*X
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
if [ ! -f "$CLASSPATH" ]; then
    echo "Notice: gradle-wrapper.jar not bundled in source archive; run 'gradle wrapper' or open in Android Studio / GitHub Actions with gradle/actions/setup-gradle."
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    fi
    exit 1
fi
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
