#!/bin/bash

# app home
export CPM_HOME=/vagrant # $HOME/projects/custom/cpm #

# core server
export JAVA_HOME=$CPM_HOME/lib/jdk1.8.0_51
export SCALA_HOME=$CPM_HOME/lib/scala-2.11.7
export PATH=$CPM_HOME/lib/sbt/bin:$JAVA_HOME/bin:$SCALA_HOME/bin:$PATH
export PATH=$CPM_HOME/mongodb/bin:$PATH

# git modules path
# core
export CPM_CORE=$CPM_HOME/core
# web server
export CPM_WEB_HOME=$CPM_HOME/web
export CPM_WEB_HTPASSWD=$CPM_WEB_HOME/private/.htpasswd
