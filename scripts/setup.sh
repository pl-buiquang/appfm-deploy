#!/bin/bash

source /vagrant/scripts/env.sh

echo "Compiling and packaging core server :"
cd $CPM_HOME/core
sbt assembly

echo "Installing python client :"
export PATH=$PATH:$HOME/.local/bin
cd $CPM_HOME/cli
pip install --user --editable .
./generate-autocomplete.sh
echo ". $CPM_HOME/cli/cpm-complete.sh" >> $HOME/.bashrc

echo "Installation complete."
echo "To use AppFM CLI make sure $HOME/.local/bin is in you PATH and source your bashrc to enable autocompletion"


