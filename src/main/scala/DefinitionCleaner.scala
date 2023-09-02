import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.StreamingQueryListener
import org.apache.spark.sql.streaming.StreamingQueryListener.{QueryProgressEvent, QueryStartedEvent, QueryTerminatedEvent}

/**
 * Implements Streaming data processor using Spark SQL Stream
 */
object DefinitionCleaner {
  Logger.getLogger("org").setLevel(Level.OFF)
  Logger.getLogger("akka").setLevel(Level.OFF)

  @transient lazy val logger: Logger = Logger.getLogger("$")

  def main(args: Array[String]): Unit = {
    logger.info("Initializing spark context...")
    val spark: SparkSession = SparkSession.builder()
      .appName(s"DefinitionCleaner")
      .master("local[*]")
      .getOrCreate()


    spark.streams.addListener(new StreamingQueryListener() {
      override def onQueryStarted(queryStarted: QueryStartedEvent): Unit = {
        println("Query started: " + queryStarted.id)
      }

      override def onQueryTerminated(queryTerminated: QueryTerminatedEvent): Unit = {
        println("Query terminated: " + queryTerminated.id)
      }

      override def onQueryProgress(queryProgress: QueryProgressEvent): Unit = {
        println("Query made progress: " + queryProgress.progress)
      }
    })

    val df = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka-broker:9093")
      .option("subscribe", "spark-streaming-topic")
      .load()

    df.printSchema()

    val tdf = df.selectExpr("CAST(value AS STRING)")
      .select("value")
      .writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .start()

    tdf.awaitTermination()
  }

}
