#!/usr/bin/env bash

apt-get update

# docker installation
apt-get install -y curl
curl -sSL https://get.docker.com/ | sh
service docker start
usermod -aG docker vagrant

# zmq lib
apt-get install -y libzmq3 libzmq3-dev

# process shell related install (python & pyzmq)
apt-get install -y --no-install-recommends \
    python python-pip python-dev gcc g++ vim net-tools curl less python-software-properties
pip install pyzmq

# mongodb
apt-get install -y mongodb-server

# docker monitoring (influx)
wget http://influxdb.s3.amazonaws.com/influxdb_0.9.4.2_amd64.deb
dpkg -i influxdb_0.9.4.2_amd64.deb
service influxdb start

# webserver (nginx + php)
#apt-get install -y nginx
#/etc/init.d/nginx start
#apt-get install -y php5-fpm
#/etc/init.d/php5-fpm restart

#jzmq
apt-get install libtool-bin
cd /vagrant/lib/jzmq/jzmq-jni
./autogen.sh
./configure
make
make install


#webserver (apache)
apt-get install -y apache2 libapache2-mod-php5
mv /etc/apache2/ports.conf /etc/apache2/ports.conf.origin
sed -e 's/Listen\s*80/Listen 8080/g' /etc/apache2/ports.conf.origin > /etc/apache2/ports.conf
cp /vagrant/web/private/appfm.conf /etc/apache2/sites-available
a2ensite appfm.conf
a2enmod rewrite
mkdir -p /vagrant/web/log
mkdir -p /vagrant/web/dokuwiki/data/cache
mkdir -p /vagrant/web/dokuwiki/data/index
touch /vagrant/web/log/custom.log
chmod a+w /vagrant/web/log/custom.log /vagrant/web/dokuwiki/data/index /vagrant/web/dokuwiki/data/cache

# mongodb php driver (for moadmin mongodb core, not for web-ui
apt-get install -y php5-dev php5-cli php-pear libsasl2-dev
pecl install mongodb
pecl install mongo

# zmq php bindings
apt-get install -y pkg-config
pecl install zmq-beta

printf "[zmq]\nextension=zmq.so\n\n[mongodb]\nextension=mongo.so" >> /etc/php5/apache2/php.ini

service apache2 restart

# additionnal disk for mongo & docker
apt-get install -y parted
parted /dev/sdb mklabel msdos
parted /dev/sdb mkpart primary 512 100%
mkfs.ext4 /dev/sdb1
mkdir /mnt/disk
echo `blkid /dev/sdb1 | awk '{print$2}' | sed -e 's/"//g'` /mnt/disk   ext4   noatime,nobarrier   0   0 >> /etc/fstab
mount /mnt/disk/


service mongodb stop
service docker stop

mv /var/lib/docker /mnt/disk
mv /var/lib/mongodb /mnt/disk

ln -s /mnt/disk/mongodb /var/lib/mongodb
ln -s /mnt/disk/docker /var/lib/docker

service mongodb start
service docker start



