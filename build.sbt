name := "get-started" // project's name
version := "0.2" // Our application version
scalaVersion := "2.12.13" // version of Scala we want to use (this should be in line with the version of Spark framework)

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.0.2",
  "org.apache.spark" %% "spark-sql" % "3.0.2",
  "org.apache.spark" %% "spark-mllib" % "3.0.2" % "provided",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.0.2",
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.0.2"
)
