import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{StringType, StructField, StructType}

/**
 * Implements Streaming data processor using Spark SQL Stream
 */
object MainApp {
  Logger.getLogger("org").setLevel(Level.OFF)
  Logger.getLogger("akka").setLevel(Level.OFF)

  @transient private lazy val logger: Logger = Logger.getLogger("$")

  def main(args: Array[String]): Unit = {
    logger.info("Initializing spark context...")
    val spark: SparkSession = SparkSession.builder()
      .appName(s"DefinitionCleaner")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    // defining input stream data type (word, definition, response_topic)
    val definitionSchema = new StructType()
      .add(StructField("word", StringType, nullable = true))
      .add(StructField("definition", StringType, nullable = true))
      .add(StructField("response_topic", StringType, nullable = true))

    // reading data from kafka topic
    val inputStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka-broker:9093")
      .option("subscribe", "spark-streaming-topic")
      .option("encoding", "UTF-8")
      .load()

    inputStream.printSchema() // debug purpose

    // Udf function to use to transform our input data
    val transformationUdf = udf((definition: String) => {
      val result = countWords(definition)
      // you can apply other transformation here as per your inspiration
      result
    })

    // perform transformation here
    val outputDf = inputStream.selectExpr("cast(value as string)")
      .select(from_json(col("value"), definitionSchema).as("data"))
      .select(col("data.word"),
        transformationUdf(col("data.definition")) // don't forget to apply the transformation
          .as("definition"))
      .select(col("word"), explode(col("definition")))
      .toDF("word", "token", "count")
      .filter(col("word") =!= col("token"))
      .filter(len(col("token")) > 2)
      .filter(length(col("token")) > 1)

    outputDf.printSchema() // debug purpose

    // displaying the transformed data to the console for debug purpose
    val streamConsoleOutput = outputDf.writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .start()

    // sending the transformed data to kafka
    outputDf.select(to_json(struct(col("word"),
        col("token"), col("count"))).as("value")) // compute a mandatory field `value` for kafka
      .writeStream
      .outputMode("append")
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka-broker:9093")
      .option("topic", "spark-streaming-dico")
      .option("checkpointLocation", "/tmp/checkpoint") // required in kafka mode (the behaviour hard coded in the api!)
      .start()

    // waiting the query to complete (blocking call)
    streamConsoleOutput.awaitTermination()
  }

  private def countWords(definition: String): Map[String, Int] = {
    val result = definition.split("\n")
      .filter(_.nonEmpty) // removing blank lines...
      .filter(!_.contains("Votre navigateur")) // removing the browser warning
      .filter(!_.contains("Définition de")) // removing the header text
      .flatMap(_.split("\\W+"))
      .foldLeft(Map.empty[String, Int]) {
        (count, word) => count + (word -> (count.getOrElse(word, 0) + 1))
      }
    result
  }
}
