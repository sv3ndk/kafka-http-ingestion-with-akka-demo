# Http streaming ingestion to Kafka

This project demonstrates a streaming setup from a paginated datasource (e.g. a DB queried page after page) into a kafka topic, via HTTP. The implementation is in scala, using [Akka Stream](https://doc.akka.io/docs/akka/current/stream/index.html), [Akka HTTP](https://doc.akka.io/docs/akka-http/current/introduction.html) and [Alpakka](https://doc.akka.io/docs/alpakka/current/index.html). 

This can be useful if the input DB and kafka are deployed on different networks, maybe in different companies, and the networking constraints makes usage HTTP mandatory. 

The setup is simple: 

* On the DB side:

    * a [back-pressured HTTP endpoint](src/main/scala/com/svend/demo/web/DbRoutes.scala#L26) provides a continuous stream of DB rows,  accepting a `fromRowId` query parameter
    *  in order to feed this endpoint, paginated queries get [lazily sent to DB](src/main/scala/com/svend/demo/web/MockDb.scala#L26) (I'm using a mock with a simple hard-coded delay here)
    *  this streaming endpoint emits DB rows as simple strings, bundled as [Server Sent Events](https://en.wikipedia.org/wiki/Server-sent_events), as part of one single HTTP connection
    *  to speed things up, 10 queries are executed concurrently on the DB
    *  this endpoint has the responsibility to emit rows with strictly increasing `rowId`. The concurrent queries above jeopardise that, and a tentative `sort` method has been added (tolerating a maximum "out of orderness").   

* On the Kafka ingestion side: 
    * A basic [Alpakka SSE source](src/main/scala/com/svend/demo/ingestion/Ingest.scala#L41) fetches data from the HTTP endpoint above and pushes them to a Kafka topic 
    *  while doing so, a `db.row.id` header is included in every record written to Kafka, keeping track of the DB row id corresponding to each kafka record
    *  Upon a restart, [KafkaRowIdReader](src/main/scala/com/svend/demo/ingestion/KafkaRowIdReader.scala#L38) reads the `db.row.id` header of the latest records in the destination topic to figure out where we left out. We then use that as query parameter 

Both sides of the solution can be started and restarted in any order, the communication will simply resume as soon as both are online. 


# How to run

## Start a local instance of kafka 

```
confluent start
```

## create a test destination topic

```
kafka-topics --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test-topic 
```

## Launch the web api

```
sbt "runMain com.svend.demo.web.DbServiceApp"
```

you can already test the download of data manualy: 

```
curl -X GET 'http://localhost:8080/events?fromRowId=5000' --limit-rate 10K
```


## Launch the Alpakka connection: 


```
sbt "runMain com.svend.demo.ingestion.Ingest"
```

## Inspect the content of the destination topic

```
kafkacat \
    -b localhost:9092 \
    -C -t test-topic \
    -o 0 \
    -f '\nKey (%K bytes): %k Value (%S bytes): %s Headers: %h\n'
```

