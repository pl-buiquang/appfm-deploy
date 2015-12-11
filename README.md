CPM Package

This package contains all what is needed to run cpm within a virtual machine based on debian jessie 64 bits


== Installation ==

add bin to your path:
export PATH=$PATH:/path/to/bin


== Getting Started ==

=== CLI ===
cpm start

=== Webinterface ===
run cpm as a service : create init.d file (that run cpm start)
go to http://localhost:8080


=== Python module ===
for now : add cpm.py to your project


=== Java module ===
add cpm-client.jar to your libraries


== Documentation ==






== Files ==
README : this file (getting started, quick doc reference)
INSTALL : more doc on how to install & configure cpm
config.yaml : configuration system (dirs,port)
scripts/env.sh : (mostly) environnement settings for run
scripts/refreshvagrantfile.sh : generate vagrant file from config.yaml
scripts/bootstrap.sh : vagrant bootstrap file
scripts/runlocalmongo.sh : run local mongo if used
...
Vagrantfile : vagrantfile
doc/VERSIONS(Roadmap,changelog) : roadmap, feature lists, change log


== System Modules ==
lib (git@versatile-world.net:~/cpm-lib.git) : Static libraries (java, scala, sbt, ...)
core (git@versatile-world.net:~/cpm-core.git) : cpm scala server kernel (primary & replica), module repository system
process-shell (git@versatile-world.net:~/cpm-process-shell.git) : python base docker file for process (_CMD)
cli (git@versatile-world.net:~/cpm-cli.git) : cpm python client interface
web-ui  (git@versatile-world.net:~/cpm-web-ui.git) : cpm web server (use dockerfile, apache, php, js?)
web-doc  (git@versatile-world.net:~/cpm-web-doc.git) : cpm wiki documentation (dockerized apache + dokuwiki) + saved data docs
modules  (git@versatile-world.net:~/cpm-modules.git) : contains default/,addons/,examples/








