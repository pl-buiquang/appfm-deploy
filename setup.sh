#!/bin/bash

git submodule update --init --recursive
mkdir -p data
mkdir -p data/corpus
mkdir -p data/results

#echo `pwd`
echo "should launch a configuration script"
echo "that set up CPM_HOME in env.sh"
echo "and setup config.yml file (cpm home and mounted dir (results, modules, corpus)"
echo "and setup additional vagrant disk path"
echo "and setup vagrant file mounted dir (results, modules, corpus)"
echo "install vagrant virtualbox virtualbox-dkms linux-headers-amd64"
echo "maybe install virtualbox guest additions too.."
echo "vagrant plugin install vagrant-vbguest"
