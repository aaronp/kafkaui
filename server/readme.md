A CRUD application using IO/ZIO
#Cloud
 1) play w/ the IntelliJ cloud plugin - what can you do?
 2) play w/ firestore
 3) CI/CD - set as entrypoint in docker container to test out 

#Testing 
Use AppClient against a route

#Issues
### Match field groupings:
Consider an array of addresses. One record could match the street of the first address, city of the second address, and zipcode of the third address.
Simple weights won't work, as three addresses with an individual matching element each isn't the same as one address with all fields matching

#TODO
 * UI to:
   * show matches (b) for any query data
   * diff any records
   * write down best matches on update
   * write down manual matches
   * write down schemas
   * bug fixes: don't allow 'Latest' or "Versions" suffix from the user on server-side
   * permissions CRUD
   * RBAC checks
   * generate UI for a schema
   * Add group/user perms for read/write to VersionedRecord, then filter on the Claim's groups on read/write

 * Refactor the collection Nav to use NavStack   
 * docker compose w/ mongo
 * integration tests
 * websocket inserts, diffs, matches
 
 
#DONE:
 * diff previous record
 * show matches (a) for saved records 
 * create compound indices on a collection which retroactively updates values
 * UI for compound indices
