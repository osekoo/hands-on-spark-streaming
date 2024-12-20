#!/bin/bash
spark-submit \
    --deploy-mode client \
    --master "$SPARK_MASTER_URL" \
    --executor-cores 4 \
    --executor-memory 2G \
    --num-executors 1 \
    --class "MainApp" \
    "target/scala-2.12/hands-on-spark-streaming_2.12-0.1.jar" \
