# Copyright (c) 2021 Måns Tegling

#####################
## SET WORKING DIR ##
#####################

setwd("F:/linkboy/data")


###############
## FUNCTIONS ##
###############


## Open gzip file connection and perform batched read to keep memory footprint low
read_dissimilarity_matrix = function(fname) {
  
  incon <- gzcon(file(fname, open="rb"))
  
  # skip header
  h = readLines(incon, 1)[[1]]
  hp = strsplit(h, split = ", ")
  
  # identify number of entries and pre-allocate dissimilarity matrix
  M = length(hp[[1]])
  D = matrix(,nrow = M, ncol = M, dimnames = c(hp, hp))
  batchSz = 1000
  batches = M/batchSz
  
  # batched reading of matrix
  idx = 1
  for (i in 1:batches) {
    b = readLines(incon, batchSz)
    for (l in b) {
      D[idx,] =  as.numeric(strsplit(l[[1]], split = ",")[[1]])
      idx = idx + 1
    }
  }
  b = readLines(incon, M - idx + 1)
  for (l in b) {
    D[idx,] =  as.numeric(strsplit(l[[1]], split = ",")[[1]])
    idx = idx + 1
  }
  
  close(incon)
  
  # Set missing dissimilarities to global average and convert to 'dist' object (smaller footprint)
  globalMean = mean(D[lower.tri(D)], na.rm=TRUE)
  D[is.na(D)] = globalMean
  D = as.dist(D)
  return(D)
}

## Compute dissimilarity matrix for clusters defined by a cutree.
## The dissimilarity between two clusters is taken as the average of all pair-wise dissimilarities between the items of the two clusters. 
compute_cluster_dissimilarity_matrix = function(Dsym, cutree_x) {
  
  clusterIds = data.frame(movieId = names(cutree_x), clusterId = unname(cutree_x))
  k = length(unique(clusterIds$clusterId))
  M = matrix(,nrow=k, ncol=k)
  for (i in 2:k) {
    a = clusterIds[clusterIds$clusterId == i, 1]
    for (j in 1:(i-1)) {
      b = clusterIds[clusterIds$clusterId == j, 1]
      M[i,j] = mean(Dsym[a,b])
    }
  }
  diag(M) = 0
  M = as.dist(M)
  return(M)
}



#############
## PROGRAM ##
#############


D = read_dissimilarity_matrix("dissimilarity-matrix.csv.gz");

# Use hierarchical clustering for dimensionality reduction (groups nearby items)
complete_hc = hclust(D, method = "complete")
complete_cut = cutree(complete_hc, k=400)

# Complete linkage gives a balanced and nice dendrogram!
plot(complete_hc, hang = -1)
rect.hclust(complete_hc, k=400, border = 2:6)

# Create a symmetric version of the dissimilarity matrix (large memory footprint!)
Dsym = as.matrix(D)

# Compute cluster-based dissimilarity which is much smaller (lower dimension) than item-based dissimilarities
M = compute_cluster_dissimilarity_matrix(Dsym, complete_cut)

# 'Unpack' the dissimilarity matrix to a full Euclidean space (multidimensional scaling)
mds = cmdscale(M, k = 12, eig = TRUE) # now 12 dimensions is more than enough!

# Plot the first 2 dimensions of the unpacked space
plot(mds$points[,1], mds$points[,2], xlab="Coordinate 1", ylab="Coordinate 2",
     main="Metric MDS", type="n")
text(mds$points[,1], mds$points[,2], labels = 1:length(mds$points[,1]), cex = 0.7)


# Plot the first 3 dimensions of the unpacked space
library(scatterplot3d)
scplot = scatterplot3d(mds$points[,1:3], angle = 45, type="n", 
     xlab = "Coordinate 1", ylab = "Coordinate 2", zlab = "Coordinate 3", main = "Metric MDS")
zz = scplot$xyz.convert(mds$points[,1], mds$points[,2], mds$points[,3])
text(zz$x, zz$y, labels = 1:length(mds$points[,1]), pos = 3, cex = 0.7)


# ------------------------------------------------------------------------------------- #
#                                                                                       #
#  Conclusion:                                                                          #
#    The two most fundamental dimensions (qualities) of movies when it comes to taste   #
#    are linear combinations of                                                         #
#      - Simple/Dialogue-light/Slapstick <-> Dialogue-heavy/Drama/Complexity            #
#      - Cheery/Fun/Bright/Happy <-> Dark/Horror/Gore                                   #
#                                                                                       #
# --------------------------------------------------------------------------------------#





##################
## EXPERIMENTAL ##
##################

library(mclust)
b = mclustBIC(mds$points[,1:4], G = seq(from = 1, to = 15, by = 2),  modelNames = c("EII", "VII", "EEI", "EVI", "VEI", "VVI", "VVE"))
mod1 = Mclust(mds$points[,1:4], x = b)
plot(mod1, what = "BIC")

#m2 = mod1$parameters$mean[,2]
#which.min(colSums((t(mdsSample) - m2)^2))

