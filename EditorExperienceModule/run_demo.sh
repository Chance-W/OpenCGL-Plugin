#!/bin/bash
export JAVA_HOME="/Users/chancew./Software/Java/zulu21.44.17_aarch64/zulu-21.jdk/Contents/Home"
# Use the classpath generated from maven
CLASSPATH=$(cat cp.txt):target/classes

# Add the necessary exports for google-java-format on JDK 16+
JVM_OPTS="--enable-preview --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"

echo "Starting Editor Experience Demo..."
"$JAVA_HOME/bin/java" $JVM_OPTS -cp "$CLASSPATH" com.opencgl.editor.experience.EditorExperienceStartApplication
