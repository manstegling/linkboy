### Stagnation is death

Exploring is watching and learning the beauty of novelty. But where to?
Fear not, you are not alone. By your side is _linkboy_ lighting the way.
Gently encouraging you to expand your comfort-zone, one step at a time.


### Let's try

Clone the repo and build the project

```
git clone https://github.com/manstegling/linkboy.git
cd linkboy/
mvn clean install
cd target/linkboy-1.0-SNAPSHOT/
```
Now you are ready to start exploring. To find the recommended path to a movie you'll
need the 'Movie ID' from MovieLens. The easiest way is to find (`-f`) it directly
using _linkboy_
```
java -jar linkboy-1.0-SNAPSHOT.jar -f tangerines
```
This will show you that 'Tangerines (2013)' has Movie ID '116411'. To find a proposed
exploration path to this movie for a "generic taste profile", simply enter the Movie ID
(`-m`) and run
```
java -jar linkboy-1.0-SNAPSHOT.jar -m 116411
```
This will give you the recommended path
```
[
  Apocalypto (2006)
  Adrift in Tokyo (Tenten) (2007)
  Blue Spring (Aoi haru) (2001)
  Address Unknown (2001)
]
[
  Courier (1987)
  Kill the Dragon (1988)
  Return, The (Vozvrashcheniye) (2003)
  Assa (1987)
]
[
  Tangerines (2013)
  In Bloom (Grzeli nateli dgeebi) (2013)
  Band's Visit, The (Bikur Ha-Tizmoret) (2007)
  Paradise Now (2005)
]
```
Now it's up to you to decide the pace. Maybe start with 'Apocalypto', followed by
'Courier'? Then watch 'The Return' before finally taking on 'Tangerines'?  

### Who are you?

Base exploration paths on your own MovieLens ratings!

Navigate to https://movielens.org/profile/settings/import-export and click 'export ratings'.
To make _linkboy_ use your ratings, simply provide the file path with the `-u` option
```
java -jar linkboy-1.0-SNAPSHOT.jar -m 116411 -u path/to/movielens-ratings.csv
```

### Math included

Did you know that _hierarchical clustering_ can be used as a regularization method? 
If you're interested in the theory behind this project, please check out the 
[Technical Documentation](doc/DOCUMENTATION.md) for details.


### Leave feedback

Feel free to reach out if you have any questions or queries! For issues, please use
Github's issue tracking functionality.

 ----

Måns Tegling, 2021
