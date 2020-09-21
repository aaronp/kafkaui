# Deleting empty indices

We have an 'IndexerInstance' with some indexing logic.
Up 'til now (2020-04-27) we would "clean up" old 'Latest' indices -- delete them if there weren't any
values referring to them.

This doesn't work when a new value arrives for a deleted index, 'cause we still have a 'versioned' record of that value.

This means when we try and create the "new" index for value 'foo' at version 0, it fails when creating the versioned entry 'cause it'll
find some old 'foo' index at version 7. 
