#!/usr/bin/env bash

sbt clean fastOptJS coverage test cucumber coverageReport coverageAggregate

cp -r target/scala-2.13/scoverage-report target

REPORT_URL="./target/scala-2.13/scoverage-report/index.html"
open $REPORT_URL
