package com.mazem3dia.waktusolat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ZonesTest {
    @Test
    public void hasJakimsSixtyZones() {
        assertEquals(60, Zones.CODES.length);
        assertEquals(60, Zones.STATES.length);
        assertEquals(60, Zones.AREAS.length);
        assertEquals(60, Zones.TOWNS.length);
    }

    @Test
    public void findsTheZoneOfMainTowns() {
        assertEquals("WLY01", Zones.nearest(3.15, 101.70));   // Kuala Lumpur
        assertEquals("SGR03", Zones.nearest(3.04, 101.44));   // Klang
        assertEquals("SGR01", Zones.nearest(3.07, 101.52));   // Shah Alam
        assertEquals("JHR02", Zones.nearest(1.46, 103.76));   // Johor Bahru
        assertEquals("PNG01", Zones.nearest(5.41, 100.33));   // George Town
        assertEquals("SBH07", Zones.nearest(5.98, 116.07));   // Kota Kinabalu
        assertEquals("SWK08", Zones.nearest(1.55, 110.35));   // Kuching
    }

    @Test
    public void neverGuessesAnIslandOrSummitZone() {
        assertEquals("JHR02", Zones.nearest(2.43, 103.84));   // Mersing, nearer Pulau Aur than Johor Bahru
        assertEquals("PRK06", Zones.nearest(4.86, 100.79));   // on Bukit Larut itself: Taiping's zone
    }

    @Test
    public void findsNothingFarFromMalaysia() {
        assertNull(Zones.nearest(51.5, -0.12));   // London
        assertNull(Zones.nearest(13.75, 100.5));  // Bangkok
    }

    @Test
    public void namesZonesByNumber() {
        assertEquals("Zon 1", Zones.name("JHR01"));
        assertEquals("Zon 7", Zones.name("PHG07"));
        assertEquals("odd", Zones.name("odd"));
    }

    @Test
    public void looksUpCodes() {
        assertEquals("WLY01", Zones.CODES[Zones.indexOf("WLY01")]);
        assertEquals(-1, Zones.indexOf("XXX99"));
    }
}
