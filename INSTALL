== Requirements/Dependencies

Vagrant + virtualbox 
OR
Docker + zmq

Java
Language SDK


Scala
http://www.scala-lang.org/
Language library


SBT
http://www.scala-sbt.org/
Build tool

MongoDB
https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-debian71-3.0.6.tgz

== Quick start

Install vagrant
Install virtualbox
$ vagrant up
$ vagrant ssh
edit /etc/apache2/ports.conf => Listen 8080
edit /etc/apache2/site-available/000-default.conf => 
      DocumentRoot /vagrant/cpm-web-ui
        <Directory /vagrant/cpm-web-ui>
          AllowOverride All
        </Directory>
edit /etc/php5/apache2/php.ini => add extension=zmq.so


== Configuration

Vagrantfile configuration : mounted dir, ports, additional vdi disk (for containing docker images)

Docker /var/lib/docker use additional disk (script automount disk and create symlink to this disk)

config.yml (cpm config) : set port, modules/data/resultdir directories

