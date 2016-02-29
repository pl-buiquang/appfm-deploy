#!/bin/bash

source /vagrant/scripts/env.sh
nohup $CPM_HOME/lib/jdk1.8.0_51/bin/java -jar $CPM_HOME/core/target/scala-2.11/cpm-core-server-assembly-1.0.jar > appfm.log &

