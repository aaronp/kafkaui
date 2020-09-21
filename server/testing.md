Testing sucks balls, but we have to do it.

You can run the production app using the 'App' in the applications folder, but that doesn't assume an environment 
(e.g. local, docker, kube, whatever), so it will fail out of the box w/ an invalid config.

The DevApp is in the application 'test' root, which means that it will start mongo for you and also provide a valid
local configuration. Happy Days.

# Integration Testing

## DevAppFeature
As an aid for integration tests, you can go one step further and run the application using the 'DevAppFeature' entrypoint
found in the integration-test project.

This will run the same app, but keep track of the before/after database state, as well as track each request/response.

If you then use the UI as normal, followed by a browser GET request to /d/aNameForWhateverYouWereDoing, then a
```
integration-test/src/test/resources/aNameForWhateverYouWereDoing.feature
integration-test/src/test/resources/database-states/aNameForWhateverYouWereDoing/before/*.snapshot
integration-test/src/test/resources/database-states/aNameForWhateverYouWereDoing/after/*.snapshot
``` 

will get generated. You may want to clean those up a little - It's not 100% lovely/ideal, and you probably don't really want
to re-run Every. Single. Rest. Request. But it gets you close.

You can then run the CucumberTest and it will spin up the application with the same 'before' database state, and assert the 
same database state as the 'after' snapshot*.

Caveat: The assertions are lenient - they ignore things like JWT values and timestamps, so those need to be tested separately.

## Breakdown

The DevAppFeature is just a convenience over the DevApp applications, the 'LiveRecorder' which knows about the dataase,
and the 'FeatureGenerator', which knows how to traverse the saved request/responses/snapshots on disk and turn those into 
cucumber features.  
