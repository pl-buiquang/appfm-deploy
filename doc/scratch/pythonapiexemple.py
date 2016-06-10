#!/usr/bin/python

import cpm

# cpm login
session = new cpm.Session("user","passwd")

file = open("/file/path")
...

runs = session.runs
modules = session.modules
env = {
  "IN":file.path
  ...
}

prevprocs = runs["modulename"].find(???).sortByDate().first()
if prevprocs :
  env += prevprocs.toMap() # = {"SOMEVAR":SOMEVALUE,...}

async = True
process = modules["modulename"].run(env,async)

if async :
  while not process.exited():
    process.getStatus()
    #process.rm() # = process.kill() + delete process
    process.getResult("SOMERESULT")
    ...

  transformedfile = process.getResult("OUTNAME")
  someresult = process.getProcess("namespace").getResult("OUT")

  for i in someresult:
    if someresult[i] == ??? :
      ...
    ...

  ...

else :
  process.getResult("...")
  ...
  module = process.getModule()
  env = process.getOriginalEnv() # getEnv() gives the current env, ie the results map + (possibly overriden) original env
  forceNew = True
  newprocess = module.run(env,async_or_not,forceNew) # create a new run even if env and moduledef have already past run result(s)
  newenv = {
    ...
  }
  newprocess2 = module.run(newenv,async_or_not)

...

process.getLog()
...


