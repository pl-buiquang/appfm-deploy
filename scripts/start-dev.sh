#!/bin/bash

source /vagrant/scripts/env.sh
cd  $CPM_CORE;
sbt -java-home $CPM_HOME/lib/jdk1.8.0_51 run $1
