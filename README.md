# Pushdown OO Taint-Flow analysis.
Ucombinator maintained fork. Original from Shuying at https://github.com/shuyingliang/pushdownoo.

## Build Changes

The build is now done with sbt.  SBT_OPTS is set in make targets to increase compile time heap/stack space.
If you run sbt compile or sbt one-jar directly without the makefile, be sure to export SBT_OPTS as done in the makefile.

## Requirements

### Scala 2.9.1
The reason didn't move to 2.10:
* StackOverflow error in Scala 2.10
* Code changes such as case class must have parameters
* Scala 2.9.1 works perfectly with Play 2.0.4

### JVM
Compiled successfully with the following JDKs:
* OpenJDK Runtime Environment (IcedTea6 1.11.5) (6b24-1.11.5-0ubuntu1~12.04.1).
* Oracle JDK 6 and 7. If you're on Mac, use the Oracle JDK. Compiling openJDK on Mac is complicated and prone to failure.

### Graphviz
For converting to svg dyck state graph. (It will get choked on large dot files)

Ensure the graphviz is installed at `/usr/local/bin/dot`.

## Compile

Compilation is done automatically when you run (see below), but you can compile class files directly:

```
cd pdafordalvik
make compile
```

or build a jar file:

```
make jar
```

## Run

Still in `pdafordalvik` folder:

```
make run ARGS="[--k <number>] [--gc] [--lra] [--aco] [--godel] [--dump-graph] \
    [--interrupt-after <number-of-states>] \
    [--interrupt-after-time <number of minutes>] \
    path/to/your/filename.apk"
```

e.g.
```
make run ARGS="--k 1 --gc --lra --aco --godel --dump-graph path/to/Bookworm.apk"
```

### For Intent Fuzzer

```
make run ARGS="--k 1 --gc --lra --aco --godel --for-intent-fuzzer --intraprocedural \
    ./test/Twitter_3.7.1.apk"
```

*Note*: The feature of producing flow-sensitive paths in text report (apposed to in graph before) with intent operations/data involves, is not yet fully tested. At least, the output is produced after the depth first search on the analyzed graphs, which can take a long time!

### For DaCapo benchmark evaluation
* The benchmark apks locates in benchmark-dacapo-apks
* During analysis, `--obranches [number]` for branch optimization to termiate fast safely.

#### TODO

-Modify state graph output to be s-expressions 
-Remove dex2sex from repo (it should be a separate project maintained by Ucombinator)
-Fix invoke bug related to dex2sex (ask Hao)
-Remove portions of play framework that are not needed
-Flatten folder structure
-Follow sbt folder structure: http://www.scala-sbt.org/0.13/tutorial/Directories.html
-Add test framework: http://www.scalatest.org
-Minimize python script usage, or decouple scala program from script pipeline
-Consider adding data files as resources:

```
import scala.io.Source

// The string argument given to getResource is a path relative to
// the resources directory.
val source = Source.fromURL(getClass.getResource("/data.xml"))
```




