#!/bin/bash

cd  `dirname $0`/cpm-core;
sbt -java-home $CPM_HOME/lib/jdk1.8.0_51 run

#cd `dirname $0`/cpm-core;
#vagrant up
