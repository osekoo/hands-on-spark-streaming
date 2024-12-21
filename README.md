# **Spark Streaming**



## **Project Overview**

This project creates a real-time data pipeline:
1. A **Python app** fetches a word’s definition from the web and pushes it into a Kafka topic.
2. A **Spark/Scala app** reads the definition, performs a word count, and publishes the results to another Kafka topic.
3. Another **Python app** consumes and displays the word count results.



## **Architecture Diagram**

```plaintext
User -> [Python App] -> Kafka Topic (definitions) -> [Spark/Scala App] -> Kafka Topic (word-count-results) -> [Python App] -> User
```



## **Prerequisites**

1. **Install Apache Kafka**
   - Use Docker for quick setup:
     - [Install Docker](https://docs.docker.com/get-docker/).
     - [Kafka with Docker Setup](https://docs.confluent.io/platform/current/quickstart/ce-docker-quickstart.html).

2. **Install Apache Spark**
   - [Download and Install Spark](https://spark.apache.org/downloads.html).

3. **Install Python**
   - [Install Python](https://www.python.org/downloads/).
   - Install required libraries:
     ```bash
     pip install kafka-python requests beautifulsoup4
     ```

4. **Install Scala and SBT**
   - [Install Scala](https://www.scala-lang.org/download/).
   - [Install SBT](https://www.scala-sbt.org/download.html).


## **Step 1: Python App - Infinite Word Input and Definition Fetcher**

### **Setup the Project**

1. Create a Python Kafka project:
   ```bash
   sbt new osekoo/kafka-py.g8
   ```
2. Follow prompts to name your project (e.g., `python-kafka-app`).
3. Go to the project folder
4. Edit `requirements.txt` and
   ```bash
      kafka-python
      beautifulsoup4~=4.12.3
      requests~=2.32.3
    ```
6. Install python requirements:
   
   ```bash
   python -m pip install -r requirements.txt
   ```



### **Code: Word Definition Producer**

Replace the default producer logic with the following code in `producer.py`:

```python
import json
from kafka import KafkaProducer
import requests
from bs4 import BeautifulSoup

BROKER = 'localhost:9092'
TOPIC = 'definitions'


def fetch_definition(word):
    """
    Fetches the definition of a word from a dictionary API or web scraping.
    """
    url = f"https://www.dictionary.com/browse/{word}"
    response = requests.get(url)
    soup = BeautifulSoup(response.text, 'html.parser')
    print(soup)
    definition_tag = soup.find('div', id='dictionary-entry-1')
    return definition_tag.text.strip() if definition_tag else None


def produce_word_and_definition():
    """
    Continuously reads words from the user, fetches definitions, and sends them to Kafka.
    """
    producer = KafkaProducer(
        bootstrap_servers=BROKER,
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )
    try:
        while True:
            word = input("Enter a word (or type 'exit' to quit): ").strip()
            if word.lower() == 'exit':
                print("Exiting producer...")
                break

            definition = fetch_definition(word)
            if not definition:
                print("ERROR: Definition not found.")
                continue
            message = {"word": word, "definition": definition}
            producer.send(TOPIC, value=message)
            print(f"Sent to Kafka: {message}")
    except KeyboardInterrupt:
        print("\nProducer stopped.")
    finally:
        producer.close()


if __name__ == "__main__":
    produce_word_and_definition()

```

### **Run the Producer**
Start the producer:
```bash
python producer.py
```

## **Step 2: Spark/Scala App - Word Count Processor**

### **Setup the Project**

1. Create a Spark Scala project:
   ```bash
   sbt new osekoo/spark-scala.g8
   ```
2. Follow prompts to name your project (e.g., `spark-word-count-streaming`) and leave the default parameters.
3. Update `build.sbt` with kafka dependencies
```scala
name := "spark-word-count-streaming"

version := "0.1"

scalaVersion := "2.12.18"

val sparkVersion = "3.5.2"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion
)
```
3. Update spark-submit with kafka dependencies
```bash
#!/bin/bash
spark-submit \
    --deploy-mode client \
    --master "$SPARK_MASTER_URL" \
    --executor-cores 4 \
    --executor-memory 2G \
    --num-executors 1 \
    --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.2,org.apache.spark:spark-streaming-kafka-0-10_2.12:3.5.2 \
    --class "MainApp" \
    "target/scala-2.12/spark-word-count-streaming_2.12-0.1.jar" \
```


### **Code: Word Count Processor**

Replace the default app logic with the following in `MainApp.scala`:

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{StringType, StructField, StructType}

/**
 * Implements Streaming data processor using Spark SQL Stream
 */
object MainApp {

  def main(args: Array[String]): Unit = {
    val kafkaBroker = "kafka-broker:9093"
    val definitionTopic = "definitions"
    val wordCountTopic = "word-count-results"

    val spark: SparkSession = SparkSession.builder()
      .appName(s"SparkWordCountStreaming")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")

    // defining input stream data type (word, definition, response_topic)
    val definitionSchema = new StructType()
      .add(StructField("word", StringType, nullable = true))
      .add(StructField("definition", StringType, nullable = true))
      .add(StructField("response_topic", StringType, nullable = true))

    // reading data from kafka topic
    val inputStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBroker)
      .option("subscribe", definitionTopic)
      .option("encoding", "UTF-8")
      .load()

    inputStream.printSchema() // debug purpose

    // Udf function to use to transform our input data
    val transformationUdf = udf(transform _)

    // perform transformation here
    val outputDf = inputStream.selectExpr("cast(value as string)")
      .select(from_json(col("value"), definitionSchema).as("data"))
      .select(col("data.word"),
        transformationUdf(col("data.word"), col("data.definition")) // don't forget to apply the transformation
          .as("definition"))
      .select(col("word"), explode(col("definition")))
      .toDF("word", "token", "count")
      .filter(len(col("token")) > 2) // filter out words with less than 2 occurrences

    outputDf.printSchema() // debug purpose

    // displaying the transformed data to the console for debug purpose
    val streamConsoleOutput = outputDf.writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .start()

    // sending the transformed data to kafka
    outputDf
      .select(to_json(struct(col("word"),
        col("token"), col("count"))).as("value")) // compute a mandatory field `value` for kafka
      .writeStream
      .outputMode("append")
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBroker)
      .option("topic", wordCountTopic)
      .option("checkpointLocation", "/tmp/checkpoint") // required in kafka mode (the behaviour hard coded in the api!)
      .start()

    // waiting the query to complete (blocking call)
    streamConsoleOutput.awaitTermination()
  }

  private def transform(word: String, definition: String): Map[String, Int] = {
    val result = definition.toLowerCase.split("\\W+") // split by non-word characters
      .filter(_.nonEmpty) // remove empty strings
      .groupBy(identity) // group by word
      .mapValues(_.length) // count words

    // e.g. remove stop words, apply stemming, etc.
    // remove stop words
    val stopWords = Set(word, "so", "a", "an", "the", "is", "are", "am", "and", "or", "not", "for", "to", "in", "on", "at", "by", "with", "as", "of", "from", "that", "this", "these", "those", "there", "here", "where", "when", "how", "why", "what", "which", "who", "whom", "whose", "whom", "whomsoever", "whosoever", "whosever", "whosesoever")
    val cleanedWords = result.filterKeys(!stopWords.contains(_))

    // apply stemming

    // you can apply other transformation here as per your inspiration

    cleanedWords
  }
}
```

### **Package the Application**
```bash
sbt package
```


### **Run Script: `run-app`**
```bash
run-app
```



## **Step 3: Python App - Word Count Consumer**

Replace the default consumer logic with the following in `consumer.py`:

```python
import json
from kafka import KafkaConsumer

BROKER = 'localhost:9092'
TOPIC = 'word-count-results'

def consume_word_counts():
    """
    Consumes word count results from Kafka and displays them.
    """
    consumer = KafkaConsumer(
        TOPIC,
        bootstrap_servers=BROKER,
        value_deserializer=lambda v: json.loads(v.decode('utf-8')),
        auto_offset_reset='earliest',
        enable_auto_commit=True
    )

    print("Waiting for word count results...")
    for message in consumer:
        result = message.value
        print(f"Word Count Result: {result}")

if __name__ == "__main__":
    consume_word_counts()
```

### **Run the Consumer**
```bash
python consumer.py
```



## **Step 4: Test the End-to-End Pipeline**

1. Start Kafka:
   ```bash
   kafka-start
   ```

2. Run the Python Producer:
   ```bash
   python producer.py
   ```

3. Run the Spark Processor:
   ```bash
   sbt package
   ```
   ```bash
   run-app
   ```

4. Run the Python Consumer:
   ```bash
   python consumer.py
   ```



## **Extensions and Exercises**

1. **Windowed Aggregation**: Modify the Spark app to compute rolling word counts over time windows.
2. **Database Integration**: Store word counts in PostgreSQL or Elasticsearch.
3. **Web Dashboard**: Create a real-time web interface using Flask or Django.
4. **Advanced NLP**: Use SpaCy or NLTK for better tokenization and stopword removal.

