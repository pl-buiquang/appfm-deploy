#!/bin/bash

free -m | sed -n 3p | awk '{print $3,$4}'
