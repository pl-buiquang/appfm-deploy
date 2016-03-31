#!/bin/bash

mkdir -p $CPM_HOME/data/db

$CPM_HOME/lib/mongodb/bin/mongod --dbpath $CPM_HOME/data/db
