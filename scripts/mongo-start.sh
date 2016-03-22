#!/bin/bash

mkdir -p $CPM_HOME/data/db

mongod --dbpath $CPM_HOME/data/db
