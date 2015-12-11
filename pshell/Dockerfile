#FROM debian:latest

FROM ubuntu:precise

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
  python python-pip python-dev gcc g++ vim net-tools curl less python-software-properties

RUN pip install pyzmq 

RUN mkdir -p /home/app
WORKDIR /home/app
ADD bin/ bin/

ENTRYPOINT ["bin/dummy.py"]
