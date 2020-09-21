Feature: Matches Do Not Include Self References


Scenario: Request/Responses to make Matches Do Not Include Self References

Given a database in the initial state like 'database-states/matchesDoNotIncludeSelfReferences/before'

When The user POSTs request '/users' with json
  """
  {"userName":"a@a.com","email":"a@a.com","password":"a"}
  """
Then they should get a 200 response with json
  """
  {"user":{"name":"a@a.com","email":"a@a.com","userId":"a@a.com","iat":1589550885442}}
  """
When The user POSTs request '/login' with json
  """
  {"usernameOrEmail":"a@a.com","password":"a"}
  """
Then they should get a 200 response with json
  """
  {"tokenAndUser":["eapXVCJ9.eyJuYW1xCJpYXQiOjE1ODk1NTA4ODY0MDF9.QAlRcHybkY=",{"name":"a@a.com","email":"a@a.com","userId":"a@a.com","iat":1589550886401}]}
  """
When The user POSTs request '/rest/data/acme' with json
  """
  {"data":{"user":{"first":"David","last":"Smith"},"addresses":[{"line1":"Main Street","city":"Eyam","postCode":"S12 345"},{"line1":"Side Street","city":"Eyam","postCode":"S54 321"}]},"userId":"","id":"alpha","version":0,"createdEpochMillis":1589550897898}
  """
Then they should get a 200 response with json
  """
  {"newVersion":0,"newValue":{"data":{"user":{"first":"David","last":"Smith"},"addresses":[{"line1":"Main Street","city":"Eyam","postCode":"S12 345"},{"line1":"Side Street","city":"Eyam","postCode":"S54 321"}]},"userId":"a@a.com","id":"alpha","version":0,"createdEpochMillis":1589550897898}}
  """
When The user GETs request '/rest/data?from=0&limit=1000'
Then they should get a 200 response with json
  """
  ["acmeLatest","acmeVersions","compoundindicesLatest","indexedvalueLatest","indexedvalueVersions","registereduserLatest","registereduserVersions"]
  """
When The user POSTs request '/rest/data/foo' with json
  """
  {"data":{"user":{"first":"Susy","last":"Smith"},"addresses":[{"line1":"Another Street","city":"","postCode":"S54 321"}]},"userId":"","id":"beta","version":0,"createdEpochMillis":1589550913194}
  """
Then they should get a 200 response with json
  """
  {"newVersion":0,"newValue":{"data":{"user":{"first":"Susy","last":"Smith"},"addresses":[{"line1":"Another Street","city":"","postCode":"S54 321"}]},"userId":"a@a.com","id":"beta","version":0,"createdEpochMillis":1589550913194}}
  """
And The user GETs request '/rest/data?from=0&limit=1000'
Then they should get a 200 response with json
  """
  ["acmeLatest","acmeVersions","compoundindicesLatest","fooLatest","fooVersions","indexedvalueLatest","indexedvalueVersions","registereduserLatest","registereduserVersions"]
  """
When The user POSTs request '/rest/data/foo' with json
  """
  {"data":{"user":{"first":"Carl","last":"Smith"},"addresses":[{"line1":"123","line2":"Main Street","city":"Eyam","postCode":"S12 345"}]},"userId":"","id":"gamma","version":0,"createdEpochMillis":1589550922795}
  """
Then they should get a 200 response with json
  """
  {"newVersion":0,"newValue":{"data":{"user":{"first":"Carl","last":"Smith"},"addresses":[{"line1":"123","line2":"Main Street","city":"Eyam","postCode":"S12 345"}]},"userId":"a@a.com","id":"gamma","version":0,"createdEpochMillis":1589550922795}}
  """
When The user GETs request '/rest/index/match/foo/gamma?version=0'
Then they should get a 200 response with json
  """
  {"allAssociations":[{"ourReference":["id"],"value":"gamma","indices":null},{"ourReference":["version"],"value":"0","indices":null},{"ourReference":["data","addresses","[0]","line2"],"value":"Main Street","indices":{"references":[{"collection":"acme","path":["addresses","[0]","line1"],"id":"alpha","version":0}]}},{"ourReference":["data","addresses","[0]","city"],"value":"Eyam","indices":{"references":[{"collection":"acme","path":["addresses","[1]","city"],"id":"alpha","version":0},{"collection":"acme","path":["addresses","[0]","city"],"id":"alpha","version":0}]}},{"ourReference":["data","user","last"],"value":"Smith","indices":{"references":[{"collection":"acme","path":["user","last"],"id":"alpha","version":0},{"collection":"foo","path":["user","last"],"id":"beta","version":0}]}},{"ourReference":["userId"],"value":"a@a.com","indices":null},{"ourReference":["data","addresses","[0]","postCode"],"value":"S12 345","indices":{"references":[{"collection":"acme","path":["addresses","[0]","postCode"],"id":"alpha","version":0}]}},{"ourReference":["createdEpochMillis"],"value":"1589550922795","indices":null}]}
  """

And the database should look like 'database-states/matchesDoNotIncludeSelfReferences/after'
