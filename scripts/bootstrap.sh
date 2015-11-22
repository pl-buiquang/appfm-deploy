#!/usr/bin/env bash

apt-get update

# docker
apt-get install -y curl
curl -sSL https://get.docker.com/ | sh
service docker start
usermod -aG docker vagrant

# zmq lib
apt-get install -y libzmq3 libzmq3-dev

# mongodb
apt-get install -y mongodb-server

# docker monitoring (influx)
wget http://influxdb.s3.amazonaws.com/influxdb_0.9.4.2_amd64.deb
dpkg -i influxdb_0.9.4.2_amd64.deb
service influxdb start

# webserver (nginx + php)
apt-get install -y nginx
/etc/init.d/nginx start
apt-get install -y php5-fpm
/etc/init.d/php5-fpm restart

# mongodb php driver
apt-get install -y php5-dev php5-cli php-pear
pecl install mongo


