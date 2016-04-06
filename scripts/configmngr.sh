#!/bin/bash

DIR=`dirname $0`
DEFAULT_USER_CONF=$HOME/.cpm/user-conf


importconf() {
  if [ -n "$1" ] ; then
    DEFAULT_USER_CONF=$1
  fi
  cp $DEFAULT_USER_CONF/conf.yml $DIR/../core/src/main/resources/conf.yml
  cp $DEFAULT_USER_CONF/env.sh $DIR/env.sh
  cp $DEFAULT_USER_CONF/start.sh $DIR/
  cp $DEFAULT_USER_CONF/settings.php $DIR/../web/settings.php
  cp $DEFAULT_USER_CONF/.htaccess $DIR/../web/.htaccess
}

exportconf() {
  if [ -n "$1" ] ; then
    DEFAULT_USER_CONF=$1
  fi
  if [ ! -d $DEFAULT_USER_CONF ] ; then
    mkdir -p $DEFAULT_USER_CONF
  fi
  cp $DIR/../core/src/main/resources/conf.yml $DEFAULT_USER_CONF/
  cp $DIR/env.sh $DEFAULT_USER_CONF/
  cp $DIR/start.sh $DEFAULT_USER_CONF/
  cp $DIR/../web/settings.php $DEFAULT_USER_CONF/
  cp $DIR/../web/.htaccess $DEFAULT_USER_CONF/
}

case "$1" in
  import)
    importconf $2
  ;;
  export)
    exportconf $2
  ;;
  *)
    echo "Usage: configmngr.sh {import|export} DIR"
    exit 1
  ;;
esac

