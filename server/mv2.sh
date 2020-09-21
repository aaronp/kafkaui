#!/usr/bin/env bash
# used by ./newname.sh to rename directories
NEWNAME="$1"
FILE="$2"
NEWPATH=`echo $FILE | sed -e "s/franz/$NEWNAME/g"`
CMD="mv $FILE $NEWPATH"
echo $CMD
eval "$CMD"
