#!/bin/bash

free | sed -n 3p | awk '{print $3,$4}'
