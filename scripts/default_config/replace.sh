#!/bin/bash

CURDIR=`dirname $0`

cp $CURDIR/conf.yml $CURDIR/../../core/src/main/resources/conf.yml 
cp $CURDIR/env.sh $CURDIR/../../scripts/env.sh 
cp $CURDIR/settings.php $CURDIR/../../web/settings.php 
cp $CURDIR/.htaccess $CURDIR/../../web/.htaccess
cp $CURDIR/servers.info $CURDIR/../../web/


