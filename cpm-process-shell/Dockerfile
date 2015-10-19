FROM debian:latest

#FROM ubuntu

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
  python python-pip python-dev gcc g++ vim net-tools

RUN pip install pyzmq 

RUN mkdir -p /home/app
WORKDIR /home/app
ADD bin/ bin/

ENTRYPOINT ["bin/cpm-process-shell.py"]
