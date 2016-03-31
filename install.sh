#!/bin/bash

DEPENDENCIES=(libtool autoconf gcc g++ pkg-config)


CURDIR=`dirname $0`

SUDO=0
if [ `whoami` == "root" ] ; then
	SUDO=1
fi

function check_dependencies {
  for d in ${DEPENDENCIES[@]}; do
    EXIST=`dpkg -l | grep $d`
    if [ -z "$EXIST" ] ; then
      echo "Missing "$d
      echo "Install it manually or run this script with sudo privilleges"
      exit 1
    fi
  done
  return 0
}

function install_dependencies {
  for d in ${DEPENDENCIES[@]}; do
    EXIST=`dpkg -l | grep $d`
    if [ -z "$EXIST" ] ; then
      echo "Missing library $d will be installed"
      apt-get install -y $d
    fi
  done
  apt-get install -y libtool-bin # for debian
}

function update_env {
  cat $CURDIR/scripts/env.dist.sh | sed -e "s/#ZMQ_LIB/export ZMQ_LIB=$1/" > $CURDIR/scripts/env.sh
}

function install_root {
  apt-get install libzmq libzmq3 libzmq3-dev

  cd $CURDIR/lib/jzmq/jzmq-jni
  ./autogen.sh
  ./configure
  make
  make install

}

function install_non_root {
  PREFIX=$HOME/.local
  mkdir -p $PREFIX

  cd $CURDIR/lib/zeromq-4.1.4
  ./autogen.sh
  ./configure --prefix=$PREFIX --without-libsodium
  make
  make install

  export CPPFLAGS=-I$PREFIX/include
  export LDFLAGS=-L$PREFIX/lib
  export LD_LIBRARY_PATH=$PREFIX/lib
  export PATH=$CURDIR/lib/jdk1.8.0_51/bin:$PATH


  cd $CURDIR/lib/jzmq/jzmq-jni
  ./autogen.sh
  ./configure --prefix=$PREFIX
  make
  make install  
}

if [ $SUDO == 1 ] ; then
  install_dependencies
  SEDREPLACE=echo "/usr/local/lib" | sed -e "s/\//\\\\\//g"
  install_root
  update_env $SEDREPLACE
else 
  check_dependencies
  if [ $? -ne 0 ] ; then
    exit 1
  fi
  SEDREPLACE=echo $PREFIX | sed -e "s/\//\\\\\//g"
  install_non_root
  update_env $SEDREPLACE
fi


source $CURDIR/scripts/env.sh


echo "Compiling and packaging core server :"
cd $CPM_HOME/core
sbt assembly

echo "Installation complete !"
echo "You can install the command line interface client as normal user by running install.sh in cli directory."

