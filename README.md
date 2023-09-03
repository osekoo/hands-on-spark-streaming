# hands-on-spark-streaming

## Preparing the streaming source

- Nous allons utiliser le projet Kafka (https://github.com/osekoo/hands-on-kafka) que nous avons réaslisé précédemment
  comme source de donnée streaming.

- Téléchargez le code source sur votre machine et modifier le fichier `dico/worker.py` de telle sorte que le producer
  publie le résultat sur le topic `spark-streaming-topic`. Ce dernier sera lu plus tard par notre application Spark.

- Modifiez la méthode `__handle_word()` comme suit:

```python
        def __handle_word(self, data: KafkaRequest):


"""
Requests the definition of the word and send it back to the requester
:return:
"""
print(f'searching {data.word} ...', end=' ')
word_def = self.crawler.get_definition(data.word)
print(f'done.')
message = KafkaResponse(data.word, word_def)
print(f'sending back the definition of {data.word} ...', end=' ')
self.producer.send(data.response_topic, value=message)
print(f'done.')

# Spark streaming add-on
print(f'sending a request to spark for further operations...', end=' ')
request = KafkaStreamingRequest(data.word, word_def, data.response_topic)
self.producer.send('spark-streaming-topic', value=request)
print(f'done.')
```

- Lancez les services kafka avec la commande `docker compose up` depuis le répertoire `/dico`

- Lancez le worker (dico/worker.py), suivez les instructions  (fournir le dictionnaire fr/en)

- Lancez le client (dico/client.py), suivez les instructions (fournir un nickname et le dictionnaire fr/en)

- Vous pouvez commencer à chercher des définitions des mots...

Remarquez que le résultat est assez brut!
L'objectif de ce lab est de reformatter ce résultat pour qu'il soit un peu plus présentable.

## Preparing the streaming processor

- Téléchargez les sources de ce projet (https://github.com/osekoo/hands-on-spark-streaming). Il s'agit d'un projet SBT.

- Créez le package avec la commande `sbt clean package`. En cas d'erreur, forcez SBT à recharger les dépendances avec la
  commande `sbt reload` et `sbt clean`.

- Analysez le fichier `build.sbt`. Deux librairies de kafka ont été rajoutées. Elles vont nous servir à lire des données
  à partir du topic Kafka que nous avons créé plus haut.

```scala
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.0.2"
"org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.0.2"
```

Notes:

1) D'autres librairies peuvent être spécifiées ici selon la source à lire (SocketStream, Twitter, Youtube, Flume,
   Materialize, etc.).
2) Les versions de toutes les librairies doivent concorder sous peine d'erreur difficilement diagnosticable.

### Analysons le code du fichier `DefinitionCleaner.scala`:

- Nous devons d'abord créer la structure de donnée à lire depuis notre flux streaming.

```scala
    // defining input stream data type (word, definition, response_topic)
val definitionSchema = new StructType()
  .add(StructField("word", StringType, nullable = true))
  .add(StructField("definition", StringType, nullable = true))
  .add(StructField("response_topic", StringType, nullable = true))
```

- Voici un exemple d'une donnée publiée sur le topic `spark-streaming-topic`:

```json
{
  "word": "orage",
  "definition": "\nDéfinition de \norage\n\n​​​\n\nVotre navigateur ne prend pas en charge audio.\n\n nom masculin\n\nPerturbation atmosphérique violente, caractérisée par des phénomènes électriques (éclairs, tonnerre), souvent accompagnée de pluie, de vent. ➙ tempête. L'orage menace, éclate, gronde. Une pluie d'orage.\n\nOrage magnétique*.\n\nOrage solaire*.\n\nau figuré Trouble qui éclate ou menace d'éclater.\n\nlocution, familier Il y a de l'orage dans l'air. ➙ électricité.\n\nMédecine Orage cytokinique : choc* cytokinique.\n\n",
  "response_topic": "topic-dictionary-fr-xxx"
}
``` 

-- `word`: le mot recherché  
-- `definition`: contient la définition du mot. Nettoyez-la (supprimer les lignes vides, etc.)  
-- `response_topic`: (ignoré ici).  

- Les données sont ensuite lues à partir du topic `spark-streaming-topic` depuis le serveur
  kafka (`kafka-broker:9093`) que nous avons lancé plus haut via `docker` (cf. projet hands-on-kafka python).

```scala
    val inputStream = spark.readStream
  .format("kafka")
  .option("kafka.bootstrap.servers", "kafka-broker:9093")
  .option("subscribe", "spark-streaming-topic")
  .load()
```

- Voici la structure de donnée lue depuis kafka.

```text
  root
  |-- key: binary (nullable = true)
  |-- value: binary (nullable = true)
  |-- topic: string (nullable = true)
  |-- partition: integer (nullable = true)
  |-- offset: long (nullable = true)
  |-- timestamp: timestamp (nullable = true)
  |-- timestampType: integer (nullable = true)
```

- Le champ `value` contient la donnée (encodée en `bytes`) qui nous intéresse.  
  Nous le transformerons en `STRING` car notre donnée est en format JSON.
- le code ci-dessous convertit le champ `value` en `string`puis en `json` avec la function `from_json()` fournie avec
  l'api spark.

```scala
    // perform transformation here
val outputDf = inputStream.selectExpr("cast(value as string)")
  .select(from_json(col("value"), definitionSchema).as("data"))
  .select(col("data.word").as("word"),
    col("data.definition") // don't forget to apply the transformation
      .as("definition")
  )
```

- on fait le choix ici d'afficher les données sur la `console`. On peut aussi bien les écrire dans un fichier/base de
  données ou autres choses.

- le mode d'écriture est `append`. Cela pourrait être `complete`. Le `console sink` ne supporte pas le mode `update`.

```scala
    val streamConsoleOutput = outputDf.writeStream
  .outputMode("append")
  .format("console")
  .option("truncate", "false")
  .start()
```

c) Pour finir, nous attendons la fin du processus avec le code `tdf.awaitTermination()`.

```scala   
// waiting the query to complete (blocking call)
streamConsoleOutput.awaitTermination()
```

## Running the App

- Après avoir packagé le projet (`sbt package`), exécutez le script `start-job.bat` ou `start-job.sh` en fonction de votre OS pour tester
l'application.  
Ce script permet de lancer les différents services docker et lancer notre package sous forme de container docker. Pour
plus de détail, analysez le fichier `docker-compose.yaml`.


## To do

Modifiez le code `DefinitionCleaner.scala`:

- Rajoutez une fonction UDF qui permet de supprimer les lignes vides du champ `definition`.  
- Publiez le résultat sur le topic `spark-streaming-dico`.
  - Notez que la donnée à publier sur kafka doit contenir une colonne nommée `value`. C'est le contenu de ce champ qui sera envoyé à kafka. 
