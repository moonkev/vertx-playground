#!/usr/bin/env sh

cd ..
./gradlew build
docker build -f deploy/Dockerfile -t phantom.sol.lan:5000/moonkev/vertx_playground:latest .
docker push phantom.sol.lan:5000/moonkev/vertx_playground:latest
