== Quick'n'Dirty installation using Vagrant+Virtualbox machine virtualization

Refer to README.md

== Requirements/Dependencies

The dependencies for the installation of AppFM manually are the following :

Core : 
- Docker (https://www.docker.com/) : application containerization solution based on LXC
- Java 8 SDK (a version is already provided in the repository)
- Scala (a version is already provided in the repository or http://www.scala-lang.org) : The Scala Language Library
- SBT (a version is already provided or http://www.scala-sbt.org/) : A build tool for scala projects
- MongoDB (a version is already provided or http://www.mongodb.org) : A document oriented database system  
- ZMQ (libzmq3 & libzmq3-dev) : zeromq socket library

CLI :
- Python & pyzmq  

Web :
- Apache
- PHP5


== Installation

=== Install dependencies

sudo ./install.sh

or 

install.sh




For an installation exemple script, refer to the file "scripts/bootstrap.sh" that is used for full installation on a Debian system virtualized under Virtualbox.

== Configuration

Environment setup:
- scripts/env.sh : see scripts section below

For the embed installation (in the README) :
- Vagrantfile : vagrant configuration (refer to http://vagrantup.com/docs for documentation) notably includes resource allocation, mounted directories, nat ports, additional vdi disk
- scripts/bootstrap.sh : the script that provides (install) the vagrant box environment

For the core server : 
- config.yml : main configuration file for the core server (symlink to core/src/main/resources/conf.yml)
- core/build.sbt : SBT build configuration file (contains java/scala dependencies)

For the command line interface :
- cli/cpm.py : the first lines (after imports) of the script defines core server host informations that should match with existing server configuration

For the web interface :
- web/.htaccess : apache local configuration
- web/settings.php : php application configuration (notably defines core server host informations)

== Scripts

- scripts/env.sh : set up the environment for installation and launching of AppFM
- scripts/setup.sh : compile/package core server java/scala into an executable jar, install python cli with bash autocompletion
- scripts/cadvisor.sh : starts the cadvisor docker monitoring utility (more information here : https://github.com/google/cadvisor)


