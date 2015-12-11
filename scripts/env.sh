#!/bin/bash


export CPM_HOME=$HOME/projects/custom/cpm #/vagrant #
export JAVA_HOME=$CPM_HOME/lib/jdk1.8.0_60
export SCALA_HOME=$CPM_HOME/lib/scala-2.11.7
export PATH=$CPM_HOME/lib/sbt/bin:$JAVA_HOME/bin:$SCALA_HOME/bin:$PATH
export PATH=$CPM_HOME/mongodb/bin:$PATH

