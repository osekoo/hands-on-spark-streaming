# hands-on-spark-streaming

## Preparing the streaming source
Nous allons utiliser le projet Kafka (https://github.com/osekoo/hands-on-kafka) que nous avons réaslisés précédemment comme source de donnée.  
Téléchargez le code source sur votre machine et modifier le fichier dico/worker.py de telle sorte que le producer publie le résultat sur un topic spécifique.  
Nommons ce topic `spark-streaming-topic`. Ce dernier sera lu plus tard par notre application Spark.  
  
Modifiez la méthode `__handle_word()`  
```
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
        self.producer.send('spark-streaming-topic', value={'data': message, 'topic': data.response_topic})
        print(f'done.')
```
Lancez les services kafka avec la commande `docker compose up` depuis le répertoire `/dico`
Lancez le worker (dico/worker.py), suivez les instructions  (fournir le dictionnaire fr/en)  
Lancez le client (dico/client.py), suivez les instructions (fournir un nickname et le dictionnaire fr/en)  
Vous pouvez chercher des définitions des mots...  

## Preparing the streaming processor
Téléchargez les sources de ce projet (https://github.com/osekoo/hands-on-spark-streaming). Il s'agit d'un projet SBT.  
Créez le package avec la commande `sbt clean package`. En cas d'erreur, forcez SBT à recharger les dépendances avec la commande `sbt reload`
Analysez le fichier `build.sbt`. Deux librairies de kafka ont été rajoutées.  Elles vont nous servir à lire des données à partir des topics Kafka que nous créés plus haut.  
```
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.0.2",
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.0.2"
```

Notes: 
1) D'autres librairies peuvent être spécifiées selon la source à lire.
2) Les versions des librairies doivent concorder sous peine d'erreurs difficilement diagnosticable.


Analyser le code du fichier `DefinitionCleaner.scala`
Vous avez remarqué que le résultat retourné par Kafka lorsqu'on effectue une recherche est très brut. Nous allons ici le reformatter pour qu'il soit plus présentable.  
a) Tout d'abord nous devons lire les données publiées sur le topic `spark-streaming-topic`  depuis le serveur kafka (`kafka-broker:9093`) que nous avons lancé plus haut.
```(scala)
    val df = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka-broker:9093")
      .option("subscribe", "spark-streaming-topic")
      .load()
```

b) Ensuite nous devons lancer la lecture en continue des données qui sont publiées sur le topic.  Le code ci-dessous permet d'atteindre cet objectif.  
```
    val tdf = df.selectExpr("CAST(value AS STRING)")
      .select("value")
      .writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .start()
```
Notes: 
  - `df.printSchema()` permet d'afficher la structure des données lues depuis kafka.  
  - le champ `value` contient la donnée qui nous intéresse. Nous le transformons en `STRING`car la donnée est en format JSON.  
  - on fait le choix ici d'afficher les données sur la console. On peut les écrire dans un fichier/base de données.  
  - le mode d'écriture est `append`. Cela pourrait être `complete` ou `update`.  
c) En fin nous attends la fin du processus  avec le code `tdf.awaitTermination()`

Une seconde! Quand est-ce qu'on transforme la donnée?
OK. Il nous faut détecter lorsqu'une nouvelle donnée est disponible. Pour ce faire nous allons utiliser les listeners.  
```
    spark.streams.addListener(new StreamingQueryListener() {
      override def onQueryStarted(queryStarted: QueryStartedEvent): Unit = {
        println("new query started: " + queryStarted.id)
      }

      override def onQueryTerminated(queryTerminated: QueryTerminatedEvent): Unit = {
        println("new query terminated: " + queryTerminated.id)
      }

      override def onQueryProgress(queryProgress: QueryProgressEvent): Unit = {
        println("new query made progress: " + queryProgress.progress)
      }
    })
```
## TODO
Voici un exemple d'une donnée lue depuis le topic `spark-streaming-topic`:  
```

```
Il faut:  
- nettoyer la donnée du champ `definition` (supprimer les lignes vides, etc.)
- Republier la donnée nettoyées sur le topic du champ `topic`.  
