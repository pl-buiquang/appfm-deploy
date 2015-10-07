#!/usr/bin/env bash

apt-get update
apt-get install -y curl
curl -sSL https://get.docker.com/ | sh
service docker start
usermod -aG docker vagrant
apt-get install -y libzmq3 libzmq3-dev
apt-get install -y mongodb-server

