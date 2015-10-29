#!/usr/bin/env bash

apt-get update
apt-get install -y curl
curl -sSL https://get.docker.com/ | sh
service docker start
usermod -aG docker vagrant
apt-get install -y libzmq3 libzmq3-dev
apt-get install -y mongodb-server
wget http://influxdb.s3.amazonaws.com/influxdb_0.9.4.2_amd64.deb
dpkg -i influxdb_0.9.4.2_amd64.deb
service influxdb start
apt-get install -y nginx
/etc/init.d/nginx start
apt-get install -y php5-fpm
/etc/init.d/php5-fpm restart
