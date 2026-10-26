package com.byd.dashcast.phase1.widget;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;

public final class InstrumentLinkReadFrameTest {

    @Test
    public void readsSingleFrameClosedAtZeroDepth() throws Exception {
        String raw = "{\"ok\":true,\"result\":\"ok\"}\n";
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(raw.getBytes("UTF-8")));
        assertEquals("{\"ok\":true,\"result\":\"ok\"}", frame);
    }

    @Test
    public void skipsLeadingWhitespaceAndTrimsFrame() throws Exception {
        String raw = "   {\"a\":1}";
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(raw.getBytes("UTF-8")));
        assertEquals("{\"a\":1}", frame);
    }

    @Test
    public void nestedObjectsAndArraysCloseOnlyAtZeroDepth() throws Exception {
        String raw = "{\"a\":{\"b\":[1,2]},\"c\":3}\nTAIL";
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(raw.getBytes("UTF-8")));
        assertEquals("{\"a\":{\"b\":[1,2]},\"c\":3}", frame);
    }

    @Test
    public void escapedQuoteAndBracesInsideStringDoNotAffectDepth() throws Exception {
        String raw = "{\"a\":\"b\\\"{c}\"}\n";
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(raw.getBytes("UTF-8")));
        assertEquals("{\"a\":\"b\\\"{c}\"}", frame);
        org.json.JSONObject o = new org.json.JSONObject(frame);
        assertEquals("b\"{c}", o.getString("a"));
    }

    @Test
    public void unbalancedFrameAtEofReturnsPartialTrimmedContent() throws Exception {
        String raw = "{\"a\":1";
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(raw.getBytes("UTF-8")));
        assertEquals("{\"a\":1", frame);
    }

    @Test
    public void unterminatedStringAtEofReturnsPartialContent() throws Exception {
        String raw = "{\"a\":\"bc";
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(raw.getBytes("UTF-8")));
        assertEquals("{\"a\":\"bc", frame);
    }

    @Test
    public void emptyStreamReturnsEmptyString() throws Exception {
        assertEquals("", InstrumentLink.readFrame(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void oversizedUnclosedFrameIsTruncatedAtFourMegabyteCap() throws Exception {
        int cap = 4 * 1024 * 1024;
        StringBuilder sb = new StringBuilder("{\"a\":\"");
        while (sb.length() < cap + 16) sb.append('x');
        String frame = InstrumentLink.readFrame(new ByteArrayInputStream(sb.toString().getBytes("UTF-8")));
        assertEquals(cap + 1, frame.length());
        assertTrue(frame.startsWith("{\"a\":\""));
    }

    @Test
    public void multiFrameStreamYieldsFirstFrameOnly() throws Exception {
        String raw = "{\"a\":1}\n{\"b\":2}\n";
        ByteArrayInputStream in = new ByteArrayInputStream(raw.getBytes("UTF-8"));
        assertEquals("{\"a\":1}", InstrumentLink.readFrame(in));
        assertEquals("", InstrumentLink.readFrame(in));
    }
}
