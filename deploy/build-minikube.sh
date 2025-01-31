#!/usr/bin/env sh

cd ..
./gradlew build
eval $(minikube -p minikube docker-env)
docker build -f deploy/Dockerfile -t moonkev/vertx_playground:1.0 .
