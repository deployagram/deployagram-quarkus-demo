# Deployagram with Quarkus - Demo App

Using Maven.

## Purpose

This repo demonstrates how to instrument a [Quarkus](https://quarkus.io/) application
with [Deployagram](https://deployagram.com).

The application itself is simple: it listens to HTTP requests for a Book Recommendation, asks another app over HTTP for
a book, then writes the suggestion to a Kafka topic and responds to the original HTTP Request.

## History

If you inspect the commits, you will find:

* Creation of a vanilla Quarkus application
* Service test and application functionality
* Deployagram instrumentation for HTTP (both server and client)
* Deployagram instrumentation for Kafka Producer

## [License](./LICENSE)


