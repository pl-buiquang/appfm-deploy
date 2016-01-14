= AppFM NLP - Application Frame Manager for NLP =

== Quick Start ==

Using default configuration, here is how you can get started.
The password of every git repository is : freepass

0. Fetch this repository
git clone git2@versatile-world.net:~/cpm.git
cd cpm

1. Install (some) Dependencies (on debian) :
sudo apt-get install git vagrant virtualbox*

2. Fetch projects modules :
git submodule update --init --recursive

3. Add an additional disk to the virtual machine :
change in the file Vagrantfile the path to a virtual disk that will be created/used as additionnal disk for the virtual machine

4. Installation (may take about 15~20 minutes) :
vagrant up

5. First run :
vagrant ssh
cd vagrant
./start.sh 

When finished you can go to http://localhost:8080

== Web interface ==

The web interface is (with default quick start configuration) accessible via http://localhost:8080

== CLI ==

see README.md in cli directory

== Further Documentation ==

see INSTALL.md for more information about how to install with/without vagrant and change default configuration
a wiki is currently available with more information about how to use appfm/cpm at versatile-world.net/wiki/work/cpm