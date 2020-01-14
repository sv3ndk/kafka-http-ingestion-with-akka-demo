# Http streaming ingestion to Kafka

This project demonstrate a streaming setup from a paginated datasource (e.g. a DB queried page after page) into a kafka topic, via HTTP. The implementation is in scala, using Akka Stream, Akka HTTP and Alpakka. 

This can be useful if the input DB and kafka are deployed on different networks, maybe in different companies, and the networking setup makes HTTP mandatory. 

The setup is simple

On the DB side:
* a [back-pressured HTTP endpoint](src/main/scala/com/svend/demo/web/DbRoutes.scala) provides a continuous stream of DB rows,  accepting a `fromRowId` as query parameter
* in order to feed this endpoint, paginated queries get [lazily sent to DB](com/svend/demo/web/MockDb.scala) (I'm using a mock with a simple hard-code delay here)
* this streaming endpoint emit DB rows as simple strings, bundled them as [Server Sent events](https://en.wikipedia.org/wiki/Server-sent_events)    
* to speed things up, up to 10 queries are executed concurrently on the DB
* this endpoint has the responsibility to emit rows having strictly increasing `rowId`. The concurrent queries above jeopardise that, and a tentative `sort` method has been added (tolerating a maximum "out of orderness").   

On the Kafka ingestion side: 
* A basic [Alpakka SSE source](com/svend/demo/ingestion/Ingest.scala) fetches data from the endpoint above and pushes them to a Kafka topic 
* while doing so, a `db.row.id` header is included in every record written to Kafka, keeping track of the DB row id corresponding to each kafka record
* Upon a restart, the [KafkaRowIdReader](com/svend/demo/ingestion/KafkaRowIdReader.scala) is reading the `db.row.id` header of the latest records in the destination topic to figure out where we left out. We then use that as query parameter 

Both side of the solution can be started and restarted in any order, the communication will simply resume as soon as both are online. 


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

