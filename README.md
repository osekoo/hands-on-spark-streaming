### **Hands-on Spark Streaming: Using Kafka as Data Source**

In this lab, you’ll build a Spark Streaming project that processes data from a Kafka topic. This hands-on experience will guide you through setting up the project, understanding the code, and running the streaming application.



### **Step 1: Set Up the Project**

#### **1. Create a New Project**
- Follow the tutorial: [https://github.com/osekoo/spark-scala.g8](https://github.com/osekoo/spark-scala.g8).
- Run the following command:
  ```bash
  sbt new osekoo/spark-scala.g8
  ```
- Enter details like project name, Scala version, and Spark version when prompted.

#### **2. Open the Project**
- Open the generated project in your favorite IDE (**IntelliJ IDEA** or **VS Code**) for easy editing and navigation.



### **Step 2: Project Structure**

#### **1. Update `build.sbt`**
Modify the `build.sbt` file to include dependencies for Spark Streaming and Kafka. The updated file should look like this:
```scala
name := "hands-on-spark-streaming"

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

#### **2. Update `MainApp.scala`**
Replace the default `MainApp.scala` with the provided Kafka streaming code. This code:
- Reads data from a Kafka topic.
- Transforms the data using a custom function (`countWords`).
- Outputs transformed data to both the console and another Kafka topic.

#### **Key Components in the Code**:
1. **Kafka Integration**:
    - Reads data from the `spark-streaming-topic`.
    - Outputs transformed data to the `spark-streaming-dico` topic.

2. **Transformation Logic**:
    - Parses incoming JSON messages using a schema.
    - Processes the `definition` field to count word occurrences.
    - Filters irrelevant data based on length and content.

3. **Output**:
    - Displays the processed data in the console for debugging.
    - Writes the final output back to Kafka.



### **Step 3: Run the Application**

#### **1. Build the Application**
- Compile and package the application into a JAR:
  ```bash
  sbt package
  ```

#### **2. Start the Spark Application**
- Use the provided `run-app` script to start the application:
  ```bash
  ./run-app
  ```

#### **3. Monitor Spark Streaming**
- Open the **Spark Dashboard** in your browser:
  ```text
  http://localhost:8080
  ```
- Observe the streaming jobs, stages, and tasks.

#### **4. Stop the Application**
- After verifying the output, stop the Spark cluster:
  ```bash
  ./spark-stop
  ```



### **Questions to Explore**

1. **Data Processing**:
    - How does the `countWords` function work?
    - What transformations are applied to the data from the Kafka topic?

2. **Kafka Integration**:
    - How is Kafka configured in the Spark application?
    - What is the purpose of the `checkpointLocation` in streaming?

3. **Streaming Debugging**:
    - What information does the Spark dashboard provide about streaming jobs?
    - How can you debug issues with the Kafka source or output?



### **Outcome**
By the end of this lab, you will:
- Understand how to integrate Spark Streaming with Kafka.
- Process streaming data with Spark SQL and custom transformations.
- Output results to the console and Kafka topics for further use.
