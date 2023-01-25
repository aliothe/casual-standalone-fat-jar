# Quarkus example using casual fat jar for XA communication with casual

## Prerequisites
Make sure that the top level fat jar is built first
Have some casual service up and running - can also be a java appserver with java inbound up and running.
There is a test application in the casual-jca repository that could be used for instance.
In the examples below its javaEcho service is being used.

## Building

```shell
$ ./gradlew clean build
```

## Running 
```shell
$ CASUAL_HOST=10.111.88.180 CASUAL_PORT=7772 java -jar quarkus/build/quarkus-app/quarkus-run.jar
```
Where the ip points to your host, for java inbound 7772 is default port.

## Testing

```shell
$ curl -d @curl-data -H content-type:application/octet-stream http://localhost:8080/casual/javaEcho
Bazinga!
```

