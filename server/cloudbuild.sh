# Cloud Build - see progress on https://console.cloud.google.com/cloud-build/builds?authuser=1&project=builddemo
#
# This script is a convenience wrapper for using google's cloud build
#
# It assumes you've already followed the GCP setup as described by:
#
# https://cloud.google.com/cloud-build/docs/overview
# https://cloud.google.com/cloud-build/docs/configuring-builds/create-basic-configuration
# https://cloud.google.com/cloud-build/docs/building/build-java
#
#
# This script assumes we have 'gcloud' set up and our ~/.boto file is set up for the right project.
#
# To initially have this work for our build project, we've had to build the 'scala-sbt' Docker build image
# as descibed in the readme.md:
#
# https://github.com/GoogleCloudPlatform/cloud-builders-community/tree/master/scala-sbt
# that is, clone that repo and run:
# gcloud builds submit . --config=cloudbuild.yaml
# in the scala-sbt examples directory
#
#
# CACHING:
# https://medium.com/se-notes-by-alexey-novakov/sbt-cache-in-gcp-cloud-build-ed9b204a8764
# Setting up the cache build entailed:
#
# 1) getting the google community build:
# git clone https://github.com/GoogleCloudPlatform/cloud-builders-community.git
#
# 2) building the images used for caching:
# cd cloud-builders-community/cache
# gcloud builds  submit . --config=cloudbuild.yaml
#
# 3) making a bucket:
# gsutil mb gs://sbt_cache_franz
#
gcloud builds submit . --config=cloudbuild.yaml
