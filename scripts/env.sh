#!/bin/bash

# core server
export CPM_HOME=/vagrant # $HOME/projects/custom/cpm #
export JAVA_HOME=$CPM_HOME/lib/jdk1.8.0_51
export SCALA_HOME=$CPM_HOME/lib/scala-2.11.7
export PATH=$CPM_HOME/lib/sbt/bin:$JAVA_HOME/bin:$SCALA_HOME/bin:$PATH
export PATH=$CPM_HOME/mongodb/bin:$PATH

# web server
export CPM_WEB_HOME=/vagrant/web
export CPM_WEB_HTPASSWD=$CPM_WEB_HOME/private/.htpasswd