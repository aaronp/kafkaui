# Project

The intent is to expose and easily queryable system which can tell us the relations/discrepancies between disparate data feed.

## Scenario
Imagine we have a data feed not fully understood of customer data from SystemA that's currently supporting some application(s).
We have a similar data feed of another customer data from SystemB in a similar position, and we want to have a unified view of the two.

One example would be "show me customers from A whose have favorite color 'blue' where their favorite color is not '#0000FF'."

In order to do that, we'd have to first associate customers between the two systems. If every time we update info on system A or B
we keep up-to-date the results of some association queries then we can know "farmer sue" in System A corresponds to "librarian ann" in System B.

We should also be able to do that w/o necessarily knowing the sensitive data. So long as we restrict our operations to equality (and not comparisions like regex or less than) 
then we'll be able to know "record id A matches record id B" w/o needed to look at the actual values.

## Plan
To support that real-time query (reads), we can perform a set of queries as we right to ensure the data is up-to-date.

If we have a query which uses fields (a,b,c), then we'd re-run that query if any of those fields have changed from the previous version.

Also, while we're doing deltas, we might as well expose that info too to get for free:
 * dynamic schema determination
 * delta feeds 

# Design

Expose writers and readers to upsert data.

On write, we also indices for that data, as well as compute/write schemas.

You can then ask what the best associations are for any record, existing or not.

You can write down the results of those associations (no association, forced association, computed association)
and query which documents have stale, mismatched or missing associations.

# API

## CRUD
# write versioned data
POST /rest/data/<collection>

# read the data
GET /rest/data/<collection>?id=XYZ
# list the data
GET /rest/data/<collection>?from=100&limit=100
# find the data (looks just like the id case)
GET /rest/data/<collection>?x.y.z=17&foo[1]=12

# delete the data
DELETE /rest/data/<collection>?id=123

## Audit / Diff

# find the data (looks just like the id case)
GET /rest/data/<collection>?x.y.z=17&foo[1]=12&version=12

# diff two versions
GET /rest/data/<collection>/diff/<id>?v1=7&v2=9

# diff specific version against latest
GET /rest/data/<collection>/diff/<id>?v1=7

# diff previous against latest
GET /rest/data/<collection>/diff/<id>

## Schemas

GET /rest/data/<collection>/schema

## Matching

# compute best association
GET /rest/association/<collection>/<id>

# save results
this looks like posting any other data

# User Experience
When creating an account, the user should be sent a link to an SDK and docker image they can run to push data to kafka in
avro, json or protobuf which will be ingested. To do that we they would just need to configure the paths of the id and version for the values. 
