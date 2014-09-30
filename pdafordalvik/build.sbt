import com.github.retronym.SbtOneJar._

oneJarSettings

name := "Anadroid"

version := "1.0"

scalaVersion := "2.9.1"

scalaSource in Compile := baseDirectory.value / "src"

// set the main class for the main 'run' task
// change Compile to Test to set it for 'test:run'
mainClass in (Compile, run) := Some("org.ucombinator.dalvik.cfa.cesk.RunAnalysis")

mainClass in (oneJar) := Some("org.ucombinator.dalvik.cfa.cesk.RunAnalysis")

// fork a new JVM for 'run' and 'test:run'
fork := true

// add a JVM option to use when forking a JVM for 'run'
javaOptions += "-XX:MaxPermSize=512m"

javaOptions += "-Xms1024m"

javaOptions += "-Xmx2048m"

javaOptions += "-Xss1536m"

// only use a single thread for building
parallelExecution := false

exportJars := true
