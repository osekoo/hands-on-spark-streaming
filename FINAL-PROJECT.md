
# **Final Project: Real-Time Fraud Detection Pipeline**

### **Goal**

Build a real-time analytics pipeline that:

* Ingests simulated payment transaction events from Kafka,
* Processes the stream with Spark Structured Streaming,
* Detects potentially fraudulent patterns,
* Outputs results to a dashboard sink or a file.


## **Project Overview**

| Component | Technology Used                                 |
| --------- | ----------------------------------------------- |
| Ingestion | Kafka Producer (Python)                         |
| Streaming | Spark Structured Streaming (Scala or PySpark)   |
| Detection | Spark windowed aggregations + filters           |
| Output    | Parquet sink, console, and Kafka topic          |
| Optional  | Local dashboard (e.g., with Flask or Streamlit) |


## **Data Format — Transaction Event (JSON)**

**Example of function to generate data sample**
```python
import json
import random
from datetime import datetime, timedelta
from pathlib import Path

from faker import Faker

fake = Faker()
rows = 1_000_000
ref_start_time = datetime(2025, 6, 1, 0, 0, 0)


def generate_transaction(start_time, delta_seconds):
    timestamp = start_time + timedelta(seconds=delta_seconds)
    return {
        "user_id": f"u{random.randint(1000, 9999)}",
        "transaction_id": f"t-{i:07}",
        "amount": round(random.uniform(5.0, 5000.0), 2),
        "currency": random.choice(["EUR", "USD", "GBP"]),
        "timestamp": timestamp.isoformat(),
        "location": fake.city(),
        "method": random.choice(["credit_card", "debit_card", "paypal", "crypto"])
    }


output_file = Path("transactions.json")

with output_file.open("w") as f:
    for i in range(rows):
        print(f"\rGenerating transaction {i}/{rows}", end="")
        f.write(json.dumps(generate_transaction(ref_start_time, i)) + "\n")
```

**Example of data structure**

```json
{
  "user_id": "u1234",
  "transaction_id": "t-001",
  "amount": 185.20,
  "currency": "EUR",
  "timestamp": "2025-06-04T10:12:33Z",
  "location": "Paris",
  "method": "credit_card"
}
```


## **Project Steps**

### 1. Kafka Producer (Python)

* Simulate 10–100 transactions per second
* Random but realistic data (multiple users, different amounts, timestamps, locations)
* Push data to Kafka topic: `transactions`

### 2. Spark Streaming Pipeline

* Read stream from Kafka topic `transactions`
* Parse and transform JSON
* Apply logic such as:

  * Flag high-value transactions over a threshold (e.g. > 1000)
  * Detect more than 3 transactions from same user in < 1 minute
  * Detect transactions in multiple countries within 5 minutes

```scala
.withWatermark("timestamp", "5 minutes")
.groupBy(window($"timestamp", "1 minute"), $"user_id")
.count()
```

### 3. Output

* Write suspicious events to a Kafka topic `fraud-alerts`, to a `parquet` file and to console
* Optionally: update a dashboard


## **Bonus: Visualization (Optional)**

* Consume `fraud-alerts` with a Python script
* Display flagged events in a simple web UI using Flask or Streamlit


## **Evaluation Criteria**

| Criteria                                | Points |
| --------------------------------------- | ------ |
| Kafka producer implemented and sending  | 03     |
| Spark pipeline correctly reading stream | 03     |
| JSON parsing and schema enforcement     | 03     |
| At least 3 fraud detection rules        | 06     |
| Output to sink (file, Kafka, console)   | 03     |
| Code quality and structure              | 02     |
| Bonus: live dashboard or alert system   | +02    |


## **Deliverables**

* Source code for producer and Spark app
* Sample output or logs
* ReadMe with instructions to run locally with Docker (Kafka + Spark)
* (Optional) Screenshot or link to dashboard

