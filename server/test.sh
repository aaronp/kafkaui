#!/usr/bin/env bash

sbt clean test cucumber docker

rm target/integration-test.log
mkdir target
rm target/integration-test-up.log
docker-compose up | tee target/integration-test-up.log &

echo "============= WAITING FOR REST SERVICE ==============="
#( tail -f -n0 target/integration-test-up.log & ) | grep -q "Test Tool up"
