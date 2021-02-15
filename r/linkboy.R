# Copyright (c) 2021 Måns Tegling

###############
## ENV SETUP ##
###############

setwd("F:/linkboy/linkboy") # Replace with your/path/to/linkboy

source("r/functions.R")
source("r/parameters.R")


#############
## PROGRAM ##
#############

# Read crunched data
tastespace = read.csv(file = gzfile(coordinates), header=TRUE)
mmap = read.csv(file = gzfile(moviemap), header = TRUE, stringsAsFactors = FALSE)

# Create normalized global taste-space such that each dimension has the same importance
full1 = merge(x = tastespace, y = mmap, by = "cluster", all = TRUE)
fscaled = full1
fscaled[,(1:n)+1] = sapply(fscaled[,(1:n)+1], normalize_percentiles)

# Read ratings for user of interest
userraw = read.csv(file = userFilename, header = TRUE, stringsAsFactors = FALSE)

# User data in global taste-space with movie names and normalized coordinates
userdata = merge(userraw, fscaled[,c(1:(n+2))], by.x = "movie_id", by.y = "movieId")

# Calculate user's rating variance per dimension in the normalized taste-space
v = numeric()
for (i in 1:n) {
  ni = by(userdata[,i+7], userdata$rating, length)
  vi = by(userdata[,i+7], userdata$rating, var)
  v[i] = sum(vi*ni)
}

# The d dimensions with the lowest variances represent the user's consistent taste
userSpace = sort(v, index.return=TRUE)$ix[1:d]

# INSPECTION/DEBUG #
userSpace
plot(v)

# create adjacency matrix in user subspace from clusters in unscaled taste space
fuser = tastespace[,-1][,userSpace]
Duser = dist(fuser)
Duser = as.matrix(Duser)


# Select start and goal movie ID
movie1 = 101973 # Disconnect
movie2 = 205076 # Downton Abbey

linkpath = best_path(D = Duser, lookup = mmap, movie1 = movie1, movie2 = movie2, jumps = 4)


## Inspect the best path
linkpath

