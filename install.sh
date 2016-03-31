#!/bin/bash



LSB_RELEASE=$(lsb_release -is)
if [ $LSB_RELEASE == "Debian" ] ; then
  DEPENDENCIES=(libtool autoconf gcc g++ pkg-config libtool-bin make)
  LIBZMQ="libzmq3 libzmq3-dev"
else
  DEPENDENCIES=(libtool autoconf gcc g++ pkg-config make)
  LIBZMQ="libzmq1 libzmq-dev"
fi
    
CURDIR=$(cd `dirname $0` && pwd)


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
}

function update_env {
  SEDREPLACECURDIR=$(echo "$CURDIR" | sed -e "s/\//\\\\\//g")
  cat $CURDIR/scripts/env.dist.sh | sed -e "s/#ZMQ_LIB/export ZMQ_LIB=$1/" | sed -e "s/#CPM_HOME/export CPM_HOME=$SEDREPLACECURDIR/" > $CURDIR/scripts/env.sh
}

function install_root {
  apt-get install -y $LIBZMQ

  export CPPFLAGS="-I$CURDIR/lib/jdk1.8.0_51/include -I$CURDIR/lib/jdk1.8.0_51/include/linux"
  export PATH=$CURDIR/lib/jdk1.8.0_51/bin:$PATH

  cd $CURDIR/lib/src/jzmq/
  ./autogen.sh
  ./configure
  make
  make install

}

function install_non_root {
  PREFIX=$HOME/.local # $CURDIR/local
  mkdir -p $PREFIX

  cd $CURDIR/lib/src/zeromq-4.1.4
  ./autogen.sh
  ./configure --prefix=$PREFIX --without-libsodium
  make
  make install

  export CPPFLAGS="-I$PREFIX/include -I$CURDIR/lib/jdk1.8.0_51/include -I$CURDIR/lib/jdk1.8.0_51/include/linux"
  export LDFLAGS=-L$PREFIX/lib
  export LD_LIBRARY_PATH=$PREFIX/lib
  export PATH=$CURDIR/lib/jdk1.8.0_51/bin:$PATH


  cd $CURDIR/lib/src/jzmq/
  ./autogen.sh
  ./configure --prefix=$PREFIX
  make
  make install  
}

if [ $SUDO == 1 ] ; then
  install_dependencies
  SEDREPLACE=$(echo "/usr/local/lib" | sed -e "s/\//\\\\\//g")
  install_root
  update_env $SEDREPLACE
else 
  check_dependencies
  if [ $? -ne 0 ] ; then
    exit 1
  fi
  SEDREPLACE=$(echo $PREFIX | sed -e "s/\//\\\\\//g")
  install_non_root
  update_env $SEDREPLACE
fi


source $CURDIR/scripts/env.sh


echo "Compiling and packaging core server :"
cd $CPM_HOME/core
export SBT_OPTS="-Xmx2G"
sbt assembly

echo "Installation complete !"
echo "You can install the command line interface client as normal user by running install.sh in cli directory."

