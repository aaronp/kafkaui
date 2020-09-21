#!/usr/bin/env bash

# a means to bootstrap a new project.
# just clone this somewhere new, then run ./newname.sh foo
#
# script to refactor 'franz' into some other project 
# usage:
# ./newname.sh someNewName
#

NEWNAME="$1"

echo "renaming franz directories to '$NEWNAME'"
find . -name franz -type d -exec ./mv2.sh $NEWNAME {} \;

echo "replacing text with $NEWNAME"
find . -path ./.git -prune -o -name '*.*' -exec sed -i .bak "s/franz/$NEWNAME/g" {} \;

echo "cleaning up .bak files"
find . -path ./.git -prune -o -name '*.bak' -exec rm -f {} \;
