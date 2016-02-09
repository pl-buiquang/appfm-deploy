= AppFM NLP - Application Frame Manager for NLP =

AppFM (also previously named cpm in source code) is a platform that manage applications defined within a frame that we refer as "modules". 

The main purpose of AppFM is to be able to :

- facilitate the use of already developped applications for testing purpose
- design applications pipeline
- provide useful built-in tools and views for common nlp process (POS tagging, dependency parser, corpus statistics, etc.)

In order to achieve these purpose, the AppFM platform is built around :

- a module implementation guide
- a core server written in Scala and leveraging Docker encapsulation ability for handling the execution and management of module and modules pipeline (both are actually the same kind of object)
- a command line interface written in Python for basic interaction with the core
- a web interface (Javascript application) for advanced viewing of module execution results and creation

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
(you will need to enter multiple times the repository password)

3. Add an additional disk to the virtual machine :
change in the file "Vagrantfile" the path to a virtual disk ("file_to_disk" variable) that will be created/used as additionnal disk for the virtual machine
eg. : file_to_disk = "$HOME/appfm_vagrant_additional_disk.vdi"
this virtual disk is used to store Docker container and application mongodb data

4. Installation (may take about 15~30 minutes) :
vagrant up
vagrant ssh
/vagrant/scripts/setup.sh

5. First run :
cd /vagrant
./start.sh 

When finished you can go to http://localhost:8080

To disconnect session from the virtual machine:
exit

To stop the server (and the virtual machine) :
vagrant halt (from anywhere within the cpm directory)

To restart :
vagrant ssh (from anywhere within the cpm directory)
cd /vagrant
./start.sh

To completly destroy the virtual machine (needs re installation (step 4)) :
vagrant destroy (from anywhere within the cpm directory)

== Web interface ==

The web interface is (with default quick start configuration) accessible via http://localhost:8080

== CLI ==

see README.md in cli directory

== Further Documentation ==

see INSTALL.md for more information about how to install with/without vagrant and change default configuration
a wiki is available in the help section of the web interface (of a newly installed or existing installation) with more information about how to use appfm/cpm.
the default user/password for editing the wiki is admin/0AAvTIz5mf
