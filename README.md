  
## Illustration of bug in scala-2.11.8 stripped-down from gopher.
=======

### Dependences:    
 
 * scala-async 0.9.5

## Usage

      sbt compile
      sbt test:compile

After this you will see next output:

      [info] Compiling 1 Scala source to /Users/rssh/work/oss/scala-gopher/target/scala-2.11/test-classes...
      [info] [running phase parser on CompileForever.scala]
      [info] [running phase namer on CompileForever.scala]
      [info] [running phase packageobjects on CompileForever.scala]
      [info] [running phase typer on CompileForever.scala]

And compiler will work forever.
