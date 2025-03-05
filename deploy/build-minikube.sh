#!/usr/bin/env sh

# Gradle Build
cd ..
 ./gradlew build

# Docker Push
eval $(minikube -p minikube docker-env)
docker build -f deploy/Dockerfile -t moonkev/vertx_playground:latest .

# Helm Push
cd deploy/helm
helm dependency update ./vertx-playground-full
helm dependency build ./vertx-playground-full

helm install \
  --set global.vertx_playground.image.repository=moonkev/vertx_playground \
  --set global.vertx_playground.image.tag=latest \
  --set global.vertx_playground.image.pullPolicy=Never \
  vertx-playground-full ./vertx-playground-full -n vertx-playground --create-namespace