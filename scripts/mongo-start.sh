#!/bin/bash

SCRIPTHOME=$(cd `dirname $0` && pwd)

source $SCRIPTHOME/env.sh

mkdir -p $CPM_HOME/data/db

if [ -z $1 ] ; then exit 1; fi

PORT=$1


  # do a simple harmless command of some sort
NOOUT=`$MONGO_HOME/bin/mongo --port $PORT --eval "db.stats()" 2> /dev/null`
RESULT=$?   # returns 0 if mongo eval succeeds

if [ $RESULT -ne 0 ] ; then
    #echo "mongodb not running"
    nohup $MONGO_HOME/bin/mongod --dbpath $CPM_HOME/data/db --port $PORT > $CPM_HOME/log &
    echo $! > $CPM_HOME/`hostname`-mongodb.pid
else
    echo "0"
fi


