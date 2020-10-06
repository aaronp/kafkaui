#!/usr/bin/env bash

# see https://github.com/wurstmeister/kafka-docker/blob/master/README.md
#
# this is a bit of a poor-man's hack to just set the HOSTNAME to this host machine's IP
#
MYIP=`ipconfig getifaddr en0`
echo "my ip is $MYIP"
[[ -f .kafka3.yml ]] && echo 'replacing .kafka3.yml' && rm .kafka3.yml
cat kafka/swarm.yml | sed 's|<HOSTNAME_COMMAND_PLACEHOLDER>|echo '"$MYIP"'|g' > .kafka3.yml

docker-compose -f .kafka3.yml up -d