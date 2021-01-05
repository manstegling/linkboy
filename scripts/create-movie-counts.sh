#!/bin/bash
#================================================================
#
# Script to count number of ratings per movie and output to file on the format <count>,<movieId>
# Using the MovieLens 25M dataset with ratings provided in file 'ratings.csv.gz'

zgrep -oP ',.*?,' ratings.csv.gz | grep -v 'movieId' | sed "s/,//g" | sort | uniq -c | sed 's/^ *//g' | sort -nr | sed 's/ /,/g' > movie-counts.csv
