#!/bin/bash
#================================================================
#
# Script to count number of ratings per user and output to file on the format <count>,<userId>
# Using the MovieLens 25M dataset with ratings provided in file 'ratings.csv.gz'

zgrep -oP '^.*?,' ratings.csv.gz | grep -v 'userId' | sed 's/,//g' | sort | uniq -c | sed 's/^ *//g' | sort -nr | sed 's/ /,/g' > user-counts.csv
