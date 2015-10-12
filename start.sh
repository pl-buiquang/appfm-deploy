#!/bin/bash

cd  `dirname $0`/cpm-core;
sbt -java-home ~/lib/jdk1.8.0_60 run

#cd `dirname $0`/cpm-core;
#vagrant up
