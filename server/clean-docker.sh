#!/usr/bin/env bash

docker rm $(docker ps -a -q) -f
docker volume prune
