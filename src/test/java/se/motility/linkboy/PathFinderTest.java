package se.motility.linkboy;

import org.junit.Test;
import se.motility.linkboy.model.MoviePath;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;

import static org.junit.Assert.*;
import static se.motility.linkboy.TestUtil.*;

public class PathFinderTest {

    private static final double DELTA = 1e-4;

    @Test
    public void pathLong() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 2);

        MoviePath path = finder.find(3, 2, null);


        assertEquals(3, path.getMov1().getId());
        assertEquals(2, path.getMov2().getId());
        assertEquals(2.4467, path.getDistance(), DELTA);

        assertEquals(3, path.getPath().size());
        assertEquals(1, path.getPath().get(0).size());
        assertEquals(1, path.getPath().get(1).size());
        assertEquals(1, path.getPath().get(2).size());

        assertEquals(3, path.getPath().get(0).get(0).getId()); //top right in the x-y plane
        assertEquals(7, path.getPath().get(1).get(0).getId()); //origo in the x-y plane
        assertEquals(2, path.getPath().get(2).get(0).getId()); //bottom left in the x-y plane
    }

    @Test
    public void pathShort() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 2);

        MoviePath path = finder.find(6, 3, null);

        assertEquals(6, path.getMov1().getId());
        assertEquals(3, path.getMov2().getId());
        assertEquals(0.3908, path.getDistance(), DELTA);

        assertEquals(2, path.getPath().size());
        assertEquals(1, path.getPath().get(0).size());
        assertEquals(1, path.getPath().get(1).size());

        assertEquals(6, path.getPath().get(0).get(0).getId()); //top right in the x-y plane
        assertEquals(3, path.getPath().get(1).get(0).getId()); //top right in the x-y plane, too

    }


}