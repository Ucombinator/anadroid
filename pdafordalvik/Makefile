EXPORT_SBT_OPTS= export SBT_OPTS="-Xmx1024M -Xss1G -XX:MaxPermSize=128M"

compile:
	$(EXPORT_SBT_OPTS); sbt compile

jar: compile
	sbt one-jar
	mkdir bin; mv target/scala-2.9.1/anadroid_2.9.1-1.0-one-jar.jar bin/anadroid.jar

clean:
	sbt clean
	rm -rf target/ bin/

run:
	$(EXPORT_SBT_OPTS); sbt "run $(ARGS)"
