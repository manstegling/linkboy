# Copyright (c) 2021 Måns Tegling

#######################
## Program arguments ##
#######################

k = 3500   # number of clusters used in regularization (cutting below the 'missing value')
n = 40     # number of dimensions in global taste-space
d = 6      # number of dimensions in user subspace of taste-space

################
## Input data ##
################

#MovieLens 25M dataset (https://grouplens.org/datasets/movielens/25m/)
matrixFilename="indata/dissimilarity-matrix.csv.gz"
moviesFilename="indata/movies.csv"
userFilename="indata/user-ratings.csv"
linksFilename="indata/links.csv"

#IMDb datasets (https://datasets.imdbws.com/)
ratingsFilename="indata/title.ratings.tsv.gz"


##############
## App data ##
##############

moviemap="data/moviemap.dat.gz"       # movie and cluster meta-data
coordinates="data/tastespace.dat.gz"  # taste-space coordinates per cluster
