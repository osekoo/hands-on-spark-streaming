#!/bin/bash

cd /app

spark-submit \
  --deploy-mode $SPARK_DEPLOY_MODE \
  --master $SPARK_MASTER_URL \
  --executor-cores $SPARK_WORKER_CORES \
  --executor-memory $SPARK_WORKER_MEMORY \
  --num-executors $SPARK_EXECUTORS \
  --class $SPARK_APP_CLASS \
  --packages $SPARK_PACKAGES \
  --conf spark.driver.extraJavaOptions="-Divy.cache.dir=/tmp -Divy.home=/tmp" \
  $SPARK_APP_PACKAGE
