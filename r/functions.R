# Copyright (c) 2021 Måns Tegling

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
