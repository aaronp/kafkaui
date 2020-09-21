 * Mongo Service Impl
 * Writing updating schemas on write (CRUD service for NamedSchemas) 
 * KeyGen encryption 
 * Saving Matches service
   - query showing missing/stale match records
   - execute matches on ^^^^^
 * CompoundIndex to support split + join combined operation
   - Use-case: stem 'firstName' into 'fullNames'   
 * UI for:
   - viewing data sources
   - posting data
   - querying data
   - querying matches
   - CRUD for indices
   - CRUD for matches
   - CRUD for weights
 * Read service over config 
 * start of new repo for helm pipeline!
 * robust RBAC service design -- tree of roles? what's the service look like?
 * integration test subproject running MainAppTest but using cucumber against docker/docker-compose
 * Redis Impl
 * Revisit Caliban Support
  
Nice-to-have 
 * docker-compose example w/ mongo, postgres, ES, etc
 * postgres support?
 * Trigger CRUD
 
DESIGN:

Upon writing, invoke triggers which take projections from the limited query results and either:
   * merge add the results to the document
   * insert the results to a new collection

