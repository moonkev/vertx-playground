#!/usr/bin/env bash

opt_p=

while getopts p opt; do
    case $opt in
        p) opt_p=true ;;
        *) echo 'error in command line parsing' >&2
           exit 1
    esac
done

shift $((OPTIND - 1))

cd ..
./gradlew build
docker build -f deploy/Dockerfile -t moonkev/vertx_playground:latest .

if [[ -v opt_p && "$opt_p" == "true" ]];
then
    docker push moonkev/vertx_playground:latest
fi
