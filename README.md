# casual fat jar

## Prerequisites

* JDK 11
* Some casual or java application server running application(s) with exported service(s).

## Motivation

Enables application XA communication with casual without having to run in an application server.
The only prerequisite is that the application can provide a supplier of a working *javax.transaction.TransactionManager*.

## Building
```shell
./gradlew clean build
```

## Usage
See [example documentation](example/README.md). 