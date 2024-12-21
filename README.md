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
2. Follow prompts to name your project (e.g., `spark-word-count-streaming`).



### **Code: Word Count Processor**

Replace the default app logic with the following in `MainApp.scala`:

```scala
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010._
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties

object MainApp {
  def main(args: Array[String]): Unit = {
    val kafkaBroker = "http://localhost:9092"

    val conf = new SparkConf().setAppName("KafkaWordCountStreaming")")
    val ssc = new StreamingContext(conf, Seconds(5))

    // Kafka consumer configuration
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> kafkaBroker,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "word-count-group",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
    val topics = Array("definitions")
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topics, kafkaParams)
    )

    // Process each definition
    val definitions = stream.map(record => record.value())
    val wordCounts = definitions
      .flatMap(record => {
        val json = ujson.read(record)
        val definition = json("definition").str
        definition.split("\\s+").map(word => (word, 1))
      })
      .reduceByKey(_ + _)

    // Publish word count results back to Kafka
    wordCounts.foreachRDD { rdd =>
      rdd.foreachPartition { partition =>
        val props = new Properties()
        props.put("bootstrap.servers", kafkaBroker)
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        val producer = new KafkaProducer[String, String](props)

        partition.foreach { case (word, count) =>
          val result = s"""{"word":"$word","count":$count}"""
          producer.send(new ProducerRecord[String, String]("word-count-results", word, result))
        }

        producer.close()
      }
    }

    ssc.start()
    ssc.awaitTermination()
  }
}
```



### **Run Script: `run-app`**
```bash
./run-app
```



## **Step 3: Python App - Word Count Consumer**

Replace the default consumer logic with the following in `word_count_consumer.py`:

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
python word_count_consumer.py
```



## **Step 4: Test the End-to-End Pipeline**

1. Start Kafka:
   ```bash
   ./kafka-start
   ```

2. Run the Python Producer:
   ```bash
   python word_definition_producer.py
   ```

3. Run the Spark Processor:
   ```bash
   ./run-app
   ```

4. Run the Python Consumer:
   ```bash
   python word_count_consumer.py
   ```



## **Extensions and Exercises**

1. **Windowed Aggregation**: Modify the Spark app to compute rolling word counts over time windows.
2. **Database Integration**: Store word counts in PostgreSQL or Elasticsearch.
3. **Web Dashboard**: Create a real-time web interface using Flask or Django.
4. **Advanced NLP**: Use SpaCy or NLTK for better tokenization and stopword removal.



This comprehensive tutorial covers the entire pipeline, providing a strong foundation for real-time data processing applications.
