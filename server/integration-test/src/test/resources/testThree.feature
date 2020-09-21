Feature: Test Three

Scenario: Request/Responses to make Test Three

Given a database in the initial state like 'database-states/testThree/before'

When The user POSTs request '/login' with json
  """
  {"usernameOrEmail":"a@a.com","password":"a"}
  """

Then they should get a 200 response with json
  """
  {"tokenAndUser":["eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJuYW1lIjoiYUBhLmNvbSIsImVtYWlsIjoiYUBhLmNvbSIsInVzZXJJZCI6ImFAYS5jb20iLCJpYXQiOjE1ODkzNzM1NTQ5NzB9.EnOMTJJwZVBvpzDgw6Du8kpEbSgZq1qmTa1_CvKwzcI=",{"name":"a@a.com","email":"a@a.com","userId":"a@a.com","iat":1589373554970}]}
  """
When The user GETs request '/rest/data?from=0&limit=1000'
Then they should get a 200 response with json
  """
  ["compoundindicesLatest","compoundindicesVersions","fooLatest","fooVersions","indexedvalueLatest","indexedvalueVersions","registereduserLatest","registereduserVersions","testoneLatest","testoneVersions"]
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
  {"offset":0,"records":[{"data":true,"userId":"a@a.com","id":"alpha","version":0,"createdEpochMillis":1589373525392},{"data":[12,3],"userId":"a@a.com","id":"beta","version":0,"createdEpochMillis":1589373535736}],"total":2}
  """

And the database should look like 'database-states/testThree/after'
