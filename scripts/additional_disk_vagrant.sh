#!/bin/bash

sudo apt-get install parted
parted /dev/sdb mklabel msdos
parted /dev/sdb mkpart primary 512 100%
mkfs.ext4 /dev/sdb1
mkdir /mnt/disk
echo `blkid /dev/sdb1 | awk '{print$2}' | sed -e 's/"//g'` /mnt/disk   ext4   noatime,nobarrier   0   0 >> /etc/fstab
mount /mnt/disk/

