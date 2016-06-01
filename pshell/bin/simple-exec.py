#!/usr/bin/python

import sys
import subprocess
import shlex

cmd = " ".join(sys.argv[1:])

try:
    exitval = subprocess.call(cmd,shell=True)
except Exception as e:
    exitval = e.message

print exitval

