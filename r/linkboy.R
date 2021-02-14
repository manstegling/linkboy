# Copyright (c) 2021 Måns Tegling

#####################
## SET WORKING DIR ##
#####################

setwd("F:/linkboy/data")

# Program arguments
k = 3200   # number of clusters used in regularization (cutting below the 'missing value')
n = 40     # number of dimensions in global taste-space
d = 5      # number of dimensions in user subspace of taste-space

# Input data
matrixFilename="dissimilarity-matrix.csv.gz"
moviesFilename="movies.csv"
userFilename="user-ratings.csv"


###############
## FUNCTIONS ##
###############


## Open gzip file connection and perform batched read to keep memory footprint low
read_dissimilarity_matrix = function(fname) {
  
  incon <- gzcon(file(fname, open="rb"))
  
  # skip header
  h = readLines(incon, 1)[[1]]
  hp = strsplit(h, split = ",")
  
  # identify number of entries and pre-allocate dissimilarity matrix
  M = length(hp[[1]])
  D = matrix(,nrow = M, ncol = M, dimnames = c(hp, hp))
  batchSz = 1000
  batches = M/batchSz
  pb = txtProgressBar(min = 0, max = batches + 1, style = 3)
  
  # batched reading of matrix
  idx = 1
  for (i in 1:batches) {
    b = readLines(incon, batchSz)
    for (l in b) {
      s = strsplit(l[[1]], split = ",")[[1]]
      s[s == "NaN"] = NA
      D[idx,] =  as.numeric(s)
      idx = idx + 1
    }
    setTxtProgressBar(pb, value = i)
  }
  b = readLines(incon, M - idx + 1)
  for (l in b) {
    s = strsplit(l[[1]], split = ",")[[1]]
    s[s == "NaN"] = NA
    D[idx,] =  as.numeric(s)
    idx = idx + 1
  }
  
  close(incon)
  setTxtProgressBar(pb, value = batches + 1)
  if (sum(is.na(D)) > 0) {
    globalMean = mean(D[lower.tri(D)], na.rm=TRUE)
    message(paste("Setting missing dissimilarities to:", globalMean))
    D[is.na(D)] = globalMean 
  }
  D = as.dist(D)
  close(pb)
  return(D)
}

## Compute dissimilarity matrix for clusters defined by a cutree.
## The dissimilarity between two clusters is taken as the average of all pair-wise dissimilarities between the items of the two clusters. 
compute_cluster_dissimilarity_matrix = function(Dsym, cutree_x) {
  clusterIds = unname(cutree_x)
  k = length(unique(clusterIds))
  M = matrix(,nrow=k, ncol=k)
  pb = txtProgressBar(min = 0, max = k*k, initial = 1, style = 3) # time increases linearly for each iteration
  for (i in 2:k) {
    setTxtProgressBar(pb, value = i*i)
    a = which(clusterIds == i)
    M[i,1:(i-1)] = sapply(1:(i-1), function(j) mean(Dsym[a, which(clusterIds == j)]))
  }
  diag(M) = 0
  M = as.dist(M)
  close(pb)
  return(M)
}


## Find the "best path" between two movies in the graph described by the provided adjacency matrix.
## The best path is the shortest path that includes exactly the number of 'jumps' specified between movie1 and movie2.
## The best path also require the jumps to be somewhat limited in distance, yielding a more regular path.
best_path <- function(D, lookup, movie1, movie2, jumps) {
  title1 = lookup$title[lookup$movieId == movie1]
  title2 = lookup$title[lookup$movieId == movie2]
  start = lookup$cluster[lookup$movieId == movie1]
  goal = lookup$cluster[lookup$movieId == movie2]
  message(paste("Searching for a path between ", title1, " (C", start ,") and ", title2, " (C", goal ,") with exactly ", jumps, " jumps", sep = ""))
  maxDist = (D[start, goal] / jumps) * 1.5
  result = best_path_internal(D, start, goal, jumps, maxDist)
  while (is.infinite(result$distance) & jumps > 1) {
    jumps = jumps - 1
    maxDist = (D[start, goal] / jumps) * 1.5
    result = best_path_internal(D, start, goal, jumps, maxDist)
    if (!is.infinite(result$distance)) {
      message(paste("Items too close (distance ", format(D[start, goal], digits = 2) ,"). A good path was found for ", jumps, 
                    " jumps with a total path distance of ", format(result$distance, digits = 2), ".", sep = ""))
    }
  }
  result$recommended = lapply(result$path, function(clusterId) lookup$title[lookup$cluster == clusterId])
  return (result)
}

## Recursive brute-force algo to find the shortest path from 'start' to 'goal' making a fixed number of 'jumps'
best_path_internal <- function(D, start, goal, jumps, maxDist, path = c(start)) {
  if (jumps == 0 & start == goal) {
    return (list("path" = path, "distance" = 0))
  } else if (jumps == 1) {
    d = D[start, goal]
    if (d > maxDist) {
      d = Inf
    }
    return (list("path" = c(path, goal), "distance" = d))
  } else if (jumps <= 0) {
    return (list("path" = path, "distance" = Inf))
  }
  res = Inf
  path0 = numeric()
  for (i in 1:length(D[,1])) {
    if (start != i & goal != i & D[start, i] < maxDist) {
      res2 = best_path_internal(D, i, goal, jumps-1, maxDist, numeric())
      d = D[start, i] + res2[[2]]
      if (d < res) {
        path0 = c(path, i, res2[[1]])
        res = d
      }
    }
  }
  return (list("path" = path0, "distance" = res))
}

## Scale and translate x so that p percentile is -1 and 1-p percentile is 1
normalize_percentiles <- function(x, p = 0.05) {
  q = quantile(x, probs = c(p, 1-p))
  return (unname(2*(x - q[[1]]) / (q[[2]] - q[[1]]) - 1))
}

#############
## PROGRAM ##
#############


# Read global dissimilarity data
D = read_dissimilarity_matrix(matrixFilename);

# Use hierarchical clustering for dimensionality reduction (groups nearby items)
complete_hc = hclust(D, method = "complete")
complete_cut = cutree(complete_hc, k=k)

# Inspect cutting height (validate the number of clusters is sensible)
plot(sort(complete_hc$height, decreasing = TRUE), cex=0.4, xlim = c(1,5000))
abline(v=k, col="red")

# Complete linkage gives a balanced and nice dendrogram!
#plot(complete_hc, hang = -1)
#abline(a = sort(complete_hc$height, decreasing = TRUE)[k], b = 0, col="red")

# Create a symmetric version of the dissimilarity matrix (large memory footprint!)
Dsym = D
rm(D)
Dsym = as.matrix(Dsym)

# Compute cluster-based dissimilarity which is much smaller (lower dimension) than item-based dissimilarities
M = compute_cluster_dissimilarity_matrix(Dsym, complete_cut)

# 'Unpack' the dissimilarity matrix to a full Euclidean space (multidimensional scaling)
mds = cmdscale(M, k = n, eig = TRUE)

# INSPECTION/DEBUG #
# Plot R-squared vs number of dimensions
plot(cumsum(mds$eig) / sum(mds$eig),
     type="l", las=1, xlim = c(0,250), ylim = c(0,1),
     xlab="Number of dimensions", ylab=expression(R^2))
abline(v=n, col="red")

# Read movies
movies1 = read.csv(file = moviesFilename, header = TRUE, stringsAsFactors = FALSE)

# Create mapping between movie names and clusters
m1 = data.frame(movieId=names(complete_cut), cluster=complete_cut, row.names=NULL, stringsAsFactors = FALSE)
mm1 = merge(m1, movies1, by = "movieId")

# Data frame containing global taste-space coordinates and associated cluster
pp1 = as.data.frame(mds$points)
pp1$cluster = 1:length(pp1$V1)

# Create normalized global taste-space such that each dimension has the same importance
full1 = merge(x = pp1, y = mm1, by = "cluster", all = TRUE)
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
fuser = mds$points[,userSpace]
Duser = dist(fuser)
Duser = as.matrix(Duser)


# Select start and goal movie ID
movie1 = 101973 # Disconnect
movie2 = 205076 # Downton Abbey

linkpath = best_path(D = Duser, lookup = mm1, movie1 = movie1, movie2 = movie2, jumps = 4)


## Inspect the best path
linkpath

