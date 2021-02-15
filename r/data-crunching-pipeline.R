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


# Read global dissimilarity data
D = read_dissimilarity_matrix(matrixFilename);

# Use hierarchical clustering for dimensionality reduction (groups nearby items)
complete_hc = hclust(D, method = "complete")
complete_cut = cutree(complete_hc, k=k)

# Inspect cutting height (validate the number of clusters is sensible)
plot(sort(complete_hc$height, decreasing = TRUE), cex=0.4, xlim = c(1,5000))
abline(v=k, col="red")

# Complete linkage gives a balanced and nice dendrogram! (Plotting whole tree may crash session!)
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

# Prepare IMDb ratings
ratings = read.csv(file = ratingsFilename, header = TRUE, sep = "\t", stringsAsFactors = FALSE)
links = read.csv(linksFilename, header = TRUE, stringsAsFactors = FALSE)
links$imdbId = paste("tt", sprintf("%07d", links$imdbId), sep = "") # modify to match id in 'ratings'
links$movieId = as.character(links$movieId)
links = merge(x = links, y = ratings, by.x = "imdbId", by.y = "tconst")

# Create mapping between movie names, clusters and IMDb ratings
movies = read.csv(file = moviesFilename, header = TRUE, stringsAsFactors = FALSE)
mtmp = data.frame(movieId=names(complete_cut), cluster=complete_cut, row.names=NULL, stringsAsFactors = FALSE)
mmap = merge(mtmp, movies, by = "movieId")
mmap = merge(x = mmap, y = links[, c("movieId","averageRating","numVotes")], by="movieId", all.x = TRUE)
mmap = mmap[order(as.numeric(mmap$movieId)),]

# Data frame containing global taste-space coordinates and associated cluster
tastespace = as.data.frame(mds$points)
tastespace = cbind(cluster = 1:nrow(tastespace), tastespace)


## OUTPUT to use in standalone app ##
write.csv(x = mmap, file = gzfile(moviemap), quote = c(3,4), row.names = FALSE)
write.csv(x = format(tastespace, digits=6, scientific=FALSE), file = gzfile(coordinates), quote = FALSE, row.names = FALSE)

