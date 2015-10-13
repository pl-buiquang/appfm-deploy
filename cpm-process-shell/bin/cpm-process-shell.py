#!/usr/bin/python

import sys
import zmq
import time
import subprocess
import shlex

rawnetinfo = subprocess.check_output(["netstat", "-r"])
netinfos = rawnetinfo.split("\n")
for netinfo in netinfos :
	elts = netinfo.split(" ")
	if(len(elts)==1):
		break
	i = 1
	while(len(elts)>i and elts[i]==""):
		i+=1
	if(elts[0] == "default" or elts[0]=="0.0.0.0"):
		host = elts[i]
		print host



if len(sys.argv) > 3 :
	name = sys.argv[1]
	port = sys.argv[2]
	cmd = " ".join(sys.argv[3:])
else :
  exit()


connectionInfo = "tcp://"+host+":"+port

context = zmq.Context()

sock = context.socket(zmq.PUSH)
sock.connect(connectionInfo)

processinfo = open("/tmp/pinfo","w")
outfile = open("/tmp/out","w")
errfile = open("/tmp/err","w")

processinfo.write(cmd+"\n")


print cmd

exitval = subprocess.call(cmd,shell=True,stdout=outfile,stderr=errfile)

print exitval
processinfo.write(str(exitval))
processinfo.close()
message = name+"\nFINISHED"
print "Sending "+message
sock.send(message)
print "Sent"
