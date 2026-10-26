package com.byd.dashcast.phase1.widget;

import org.junit.Test;

import static org.junit.Assert.*;

public final class MusicCollectorLineIndexTest {

    @Test
    public void computeLineIndexReturnsMinusOneForNullTimes() {
        assertEquals(-1, MusicCollector.computeLineIndex(null, 5000L));
        assertEquals(-1, MusicCollector.computeLineIndex(null, 0L));
    }

    @Test
    public void computeLineIndexReturnsMinusOneForEmptyTimes() {
        assertEquals(-1, MusicCollector.computeLineIndex(new long[0], 0L));
        assertEquals(-1, MusicCollector.computeLineIndex(new long[0], 999999L));
    }

    @Test
    public void computeLineIndexPicksLastTimeNotAfterPositionPlusOffsetWindow() {
        assertEquals(1, MusicCollector.computeLineIndex(new long[]{0L, 1000L}, 500L));
        assertEquals(1, MusicCollector.computeLineIndex(new long[]{0L, 1000L}, 0L));
        assertEquals(0, MusicCollector.computeLineIndex(new long[]{0L, 1000L}, -1000L));
        assertEquals(2, MusicCollector.computeLineIndex(new long[]{0L, 1000L, 3000L}, 2500L));
        assertEquals(2, MusicCollector.computeLineIndex(new long[]{0L, 1000L, 3000L}, 99999L));
    }
}
