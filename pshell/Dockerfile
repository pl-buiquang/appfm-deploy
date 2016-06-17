#FROM debian:latest

FROM ubuntu:trusty

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
  ca-certificates python python-pip python-dev gcc g++ vim net-tools curl less python-software-properties

RUN pip install pyzmq 

RUN mkdir -p /home/pshell
WORKDIR /home/pshell
ADD bin/ bin/

ENTRYPOINT ["bin/dummy.py"]
