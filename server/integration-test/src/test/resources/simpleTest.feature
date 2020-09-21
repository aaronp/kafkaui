Feature: Simple Test

  @debug
Scenario: Request/Responses to make Simple Test

Given a database in the initial state like 'database-states/simpleTest/before'

When The user POSTs request '/users' with json
  """
  {"userName":"a@a.com","email":"a@a.com","password":"a"}
  """
Then they should get a 200 response with json
  """
  {"user":{"name":"a@a.com","email":"a@a.com","userId":"a@a.com","iat":1589373515722}}
  """
When The user POSTs request '/login' with json
  """
  {"usernameOrEmail":"a@a.com","password":"a"}
  """
Then they should get a 200 response with json
  """
  {"tokenAndUser":["eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJuYW1lIjoiYUBhLmNvbSIsImVtYWlsIjoiYUBhLmNvbSIsInVzZXJJZCI6ImFAYS5jb20iLCJpYXQiOjE1ODkzNzM1MTY3Mjd9.PJSx--PFvQZoN6I6k1etS2Uk0hgEB93ADRUyTFzVLMQ=",{"name":"a@a.com","email":"a@a.com","userId":"a@a.com","iat":1589373516727}]}
  """
When The user GETs request '/rest/data?from=0&limit=1000'
Then they should get a 200 response with json
  """
  ["registereduserLatest","registereduserVersions"]
  """
When The user GETs request '/rest/data?from=0&limit=1000'
Then they should get a 200 response with json
  """
  ["registereduserLatest","registereduserVersions"]
  """
When The user GETs request '/rest/compoundindices/testone?version=latest'
Then they should get a 200 response with json
  """
  null
  """
When The user POSTs request '/rest/search' with json
  """
  {"collection":"testone","queryString":"","limit":{"from":0,"limit":100}}
  """
Then they should get a 200 response with json
  """
  {"offset":0,"records":[],"total":0}
  """
When The user POSTs request '/rest/data/testone' with json
  """
  {"data":true,"userId":"","id":"alpha","version":0,"createdEpochMillis":1589373525392}
  """
Then they should get a 200 response with json
  """
  {"newVersion":0,"newValue":{"data":true,"userId":"a@a.com","id":"alpha","version":0,"createdEpochMillis":1589373525392}}
  """
When The user GETs request '/rest/compoundindices/testone?version=latest'
Then they should get a 200 response with json
  """
  null
  """
When The user POSTs request '/rest/data/testone' with json
  """
  {"data":[12,3],"userId":"","id":"beta","version":0,"createdEpochMillis":1589373535736}
  """
Then they should get a 200 response with json
  """
  {"newVersion":0,"newValue":{"data":[12,3],"userId":"a@a.com","id":"beta","version":0,"createdEpochMillis":1589373535736}}
  """

And the database should look like 'database-states/simpleTest/after'
