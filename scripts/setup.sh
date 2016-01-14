#!/bin/bash

source /vagrant/scripts/env.sh

echo "Compiling and packaging core server :"
cd $CPM_HOME/core
sbt assembly

echo "Installing python client :"
cd $CPM_HOME/cli
pip install .
./generate-autocomplete.sh
echo ". $CPM_HOME/cli/cpm-complete.sh" >> /home/vagrant/.bashrc


