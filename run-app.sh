#!/bin/bash

echo "Stopping Spark cluster..."
docker rm -f spark-submit
docker rm -f spark-worker
docker rm -f spark-master

echo "Starting Spark cluster..."
docker-compose up -d

echo "Waiting for Spark master to be ready..."
sleep 4

echo "Submitting Spark job..."
docker exec -it spark-submit dos2unix /app/spark-submit.sh
docker exec -it spark-submit chmod +x /app/spark-submit.sh
docker exec -it spark-submit /app/spark-submit.sh
echo "Spark job submitted."
