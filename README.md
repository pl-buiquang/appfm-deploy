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
sudo apt-get install git vagrant virtualbox 

2. Fetch projects modules :
git submodule update --init --recursive
(you will need to enter multiple times the repository password)

3. Configure virtual machine : Set up resources and add an additional disk to the virtual machine :
change in the file "Vagrantfile" the path to a virtual disk ("file_to_disk" variable) that will be created/used as additionnal disk for the virtual machine
eg. : file_to_disk = "$HOME/appfm_vagrant_additional_disk.vdi"
this virtual disk is used to store Docker container and application mongodb data and is set to size 100G

default resources allocated are 2 cpus and 4G of ram (you can change this in the proper fields in the Vagrantfile : eg. v.customize ["modifyvm", :id, "--memory","4096"])
Note that depending of your system, you may need to change other settings in the Vagrantfile like port mapping.

If the error "Stderr: VBoxManage: error: Could not find a controller named 'SATA Controller'" appears, add 
v.customize ["storagectl", :id, "--name", "SATA Controller", "--add", "sata"]
before the line 
v.customize ['storageattach', :id, '--storagectl', 'SATA Controller', '--port', 1, '--device', 0, '--type', 'hdd', '--medium', file_to_disk]



4. Installation (may take about 10~30 minutes) :
vagrant up
vagrant ssh
/vagrant/scripts/setup.sh


5. First run (takes a long the first time since it builds base containers):
cd /vagrant
./start.sh  (or ./start-daemon.sh)

When finished you can go to http://localhost:8080

To disconnect session from the virtual machine:
exit

To stop the server (and the virtual machine) :
vagrant halt (from outside the virtual machine session and anywhere within the cpm directory)

To restart :
vagrant ssh (from anywhere within the cpm directory)
cd /vagrant
./start.sh

To completly destroy the virtual machine (needs re installation (step 4)) :
vagrant destroy (from anywhere within the cpm directory)

== First Steps ==

If installed within a virtual machine (quick start instructions), you may also want to install the cli within your host to be able to use appfm from outside the vm.
To do so, go the cli subproject directory and follow the quick install guide in the README 

With default installation, there is some modules already availables.
Following instructions assume that you didn't change the default paths configuration.

=== Corpus / Data ===

There is some example data in the data/corpus directory, you can add more within this directory.
Only data stored in this directory is visible by applications of AppFM.
You can find the path of the data directory in config.yml (if running under a virtual machine, you have to change the Vagrantfile as well)

Results are by default stored in data/results

Warning ! All the paths are relative to the virtual machine filesystem tree !

=== Modules ===

Modules are located in the modules directory. More information for how to implement modules is in the wiki.
Since documentation isn't yet complete. You can also look into the .module files of already implemented modules interface frame.

=== Examples ===

Listing availables modules :
cpm module ls

Running module synchronously :
- extract pdf content into html :
cpm module run pdfbox@munshi --arg IN_DIR:/vagrant/data/corpus/some-pdfs --sync

Running module asynchronously :
- apply bonsai parser onto extracted content (may take several minutes up to 30min.. :/)
cpm module run bonsai-parser@munshi --arg IN_DIR:[insert_result_dir_of_previous_run]

Get process information :
cpm process get [insert_process_id_of_previous_command]

View process results :
cpm process view [pid]
cpm process view [pid] [outputname]

...

Note that you can view the results and process via the webinterface





== Web interface ==

The web interface is (with default quick start configuration) accessible via http://localhost:8080

== CLI ==

see README.md in cli directory

== Further Documentation ==

see INSTALL.md for more information about how to install with/without vagrant and change default configuration
a wiki is available in the help section of the web interface (of a newly installed or existing installation) with more information about how to use appfm/cpm.
the default user/password for editing the wiki is admin/0AAvTIz5mf
