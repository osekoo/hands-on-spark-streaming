REM c:\dev\bin\sbt\bin\sbt.bat clean package

docker build -t osekoo/spark-streaming-app .

docker compose down --remove-orphans
docker-compose up spark-streaming-worker -d
docker-compose up spark-streaming-app