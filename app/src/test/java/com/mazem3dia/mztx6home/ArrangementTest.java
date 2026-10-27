package com.mazem3dia.mztx6home;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ArrangementTest {
    private static final List<String> AZ = Arrays.asList("a", "b", "c", "d");

    @Test
    public void noSavedOrderKeepsAtoZ() {
        assertEquals(AZ, Arrangement.order(AZ, Collections.emptyList()));
    }

    @Test
    public void savedOrderFirstThenNewAppsAtoZ() {
        assertEquals(Arrays.asList("c", "a", "b", "d"), Arrangement.order(AZ, Arrays.asList("c", "a")));
    }

    @Test
    public void uninstalledAppsInSavedOrderAreSkipped() {
        assertEquals(Arrays.asList("b", "a", "c", "d"), Arrangement.order(AZ, Arrays.asList("gone", "b", "a")));
    }

    // A grid of 7 tiles, 3 wide:   0 1 2
    //                              3 4 5
    //                              6
    @Test
    public void leftAndRightStopAtTheEnds() {
        assertEquals(0, Arrangement.moveTarget(0, 7, 3, -1, 0));
        assertEquals(1, Arrangement.moveTarget(0, 7, 3, 1, 0));
        assertEquals(6, Arrangement.moveTarget(6, 7, 3, 1, 0));
    }

    @Test
    public void leftAndRightWrapAcrossRows() {
        // Order is one list: right from the end of a row continues on the next.
        assertEquals(3, Arrangement.moveTarget(2, 7, 3, 1, 0));
        assertEquals(2, Arrangement.moveTarget(3, 7, 3, -1, 0));
    }

    @Test
    public void upStopsAtTheTopRow() {
        assertEquals(1, Arrangement.moveTarget(1, 7, 3, 0, -1));
        assertEquals(1, Arrangement.moveTarget(4, 7, 3, 0, -1));
    }

    @Test
    public void downIntoAShortLastRowLandsOnTheLastTile() {
        assertEquals(3, Arrangement.moveTarget(0, 7, 3, 0, 1));
        assertEquals(6, Arrangement.moveTarget(3, 7, 3, 0, 1));
        assertEquals(6, Arrangement.moveTarget(5, 7, 3, 0, 1));
    }

    @Test
    public void downStopsAtTheLastRow() {
        assertEquals(6, Arrangement.moveTarget(6, 7, 3, 0, 1));
        assertEquals(4, Arrangement.moveTarget(4, 6, 3, 0, 1));
    }

    @Test
    public void moveShiftsTheOthers() {
        List<String> list = new ArrayList<>(AZ);
        Arrangement.move(list, 0, 2);
        assertEquals(Arrays.asList("b", "c", "a", "d"), list);
        Arrangement.move(list, 3, 0);
        assertEquals(Arrays.asList("d", "b", "c", "a"), list);
    }
}
