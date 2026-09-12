package com.zerodhatech.ticker;

import com.zerodhatech.models.Depth;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser tests for the call auction packets. Nothing here touches the network: every packet is a
 * synthetic byte array, and the ticker is built with placeholder strings so it never connects.
 */
class KiteTickerCasPacketTest {

    private static final double DELTA = 1e-9;

    private KiteTicker ticker;

    @BeforeEach
    void setUp() {
        ticker = new KiteTicker("placeholder-access-token", "placeholder-api-key");
    }

    private Tick parseOne(byte[] payload) {
        ArrayList<Tick> ticks = ticker.parseBinary(Packets.frame(payload));
        assertEquals(1, ticks.size(), "one payload in, one tick out");
        return ticks.get(0);
    }

    /**
     * The auction block is appended after the depth book, so a 200 byte packet must decode
     * identically to the 184 byte one for every field that already existed.
     */
    @Test
    void twoHundredBytePacketMatchesTheHundredAndEightyFourByteOneFieldForField() {
        Tick full = parseOne(Packets.full184());
        Tick cas = parseOne(Packets.fullCas200(142600, 142480, 25000L));

        assertEquals(full.getInstrumentToken(), cas.getInstrumentToken());
        assertEquals(full.getLastTradedPrice(), cas.getLastTradedPrice(), DELTA);
        assertEquals(full.getLastTradedQuantity(), cas.getLastTradedQuantity(), DELTA);
        assertEquals(full.getAverageTradePrice(), cas.getAverageTradePrice(), DELTA);
        assertEquals(full.getVolumeTradedToday(), cas.getVolumeTradedToday());
        assertEquals(full.getTotalBuyQuantity(), cas.getTotalBuyQuantity(), DELTA);
        assertEquals(full.getTotalSellQuantity(), cas.getTotalSellQuantity(), DELTA);
        assertEquals(full.getOpenPrice(), cas.getOpenPrice(), DELTA);
        assertEquals(full.getHighPrice(), cas.getHighPrice(), DELTA);
        assertEquals(full.getLowPrice(), cas.getLowPrice(), DELTA);
        assertEquals(full.getClosePrice(), cas.getClosePrice(), DELTA);
        assertEquals(full.getChange(), cas.getChange(), DELTA);
        assertEquals(full.getLastTradedTime(), cas.getLastTradedTime());
        assertEquals(full.getTickTimestamp(), cas.getTickTimestamp());
        assertEquals(full.getOi(), cas.getOi(), DELTA);
        assertEquals(full.getOpenInterestDayHigh(), cas.getOpenInterestDayHigh(), DELTA);
        assertEquals(full.getOpenInterestDayLow(), cas.getOpenInterestDayLow(), DELTA);
        assertDepthEquals(full, cas);

        // And only the 200 byte packet carries the auction block.
        assertEquals(KiteTicker.modeFull, full.getMode());
        assertEquals(0d, full.getReferenceLimitPrice(), DELTA);
        assertEquals(0d, full.getIndicativeClosePrice(), DELTA);
        assertEquals(0L, full.getTotalImbalanceQty());

        assertEquals(KiteTicker.modeFullCAS, cas.getMode());
        assertEquals("full_cas", KiteTicker.modeFullCAS);
        assertEquals(1426.00d, cas.getReferenceLimitPrice(), DELTA);
        assertEquals(1424.80d, cas.getIndicativeClosePrice(), DELTA);
        assertEquals(25000L, cas.getTotalImbalanceQty());
    }

    /** A sell side imbalance arrives negative and must survive as a negative long. */
    @Test
    void carriesANegativeImbalanceThroughUnchanged() {
        Tick cas = parseOne(Packets.fullCas200(142600, 142480, -1234567L));

        assertEquals(-1234567L, cas.getTotalImbalanceQty());
    }

    /** The prices are unsigned on the wire, so a value with the top bit set must not go negative. */
    @Test
    void readsAuctionPricesAsUnsigned() {
        Tick cas = parseOne(Packets.fullCas200(0xFFFFFFFF, 0x80000000, 0L));

        assertEquals(42949672.95d, cas.getReferenceLimitPrice(), 1e-6);
        assertEquals(21474836.48d, cas.getIndicativeClosePrice(), 1e-6);
    }

    /** The 36 byte index packet is the 32 byte one plus the indicative close at offset 32. */
    @Test
    void readsTheIndicativeCloseFromAThirtySixByteIndexPacket() {
        Tick index = parseOne(Packets.index36(2343580));

        assertEquals(Packets.INDEX_TOKEN, index.getInstrumentToken());
        assertFalse(index.isTradable(), "an index is not tradable");
        assertEquals(23435.10d, index.getLastTradedPrice(), DELTA);
        assertEquals(23510.00d, index.getHighPrice(), DELTA);
        assertEquals(23310.00d, index.getLowPrice(), DELTA);
        assertEquals(23400.00d, index.getOpenPrice(), DELTA);
        assertEquals(23380.00d, index.getClosePrice(), DELTA);
        assertNotNull(index.getTickTimestamp());
        assertEquals(Packets.TICK_TIME_SECONDS * 1000L, index.getTickTimestamp().getTime());
        assertEquals(KiteTicker.modeFullCAS, index.getMode());
        assertEquals(23435.80d, index.getIndicativeClosePrice(), DELTA);
    }

    /** The shorter index packet keeps decoding the way it always did, with no auction fields. */
    @Test
    void leavesTheThirtyTwoByteIndexPacketAlone() {
        Tick index = parseOne(Packets.index32());

        assertEquals(KiteTicker.modeFull, index.getMode());
        assertEquals(23435.10d, index.getLastTradedPrice(), DELTA);
        assertEquals(0d, index.getIndicativeClosePrice(), DELTA);
    }

    /** Regression guard: every field of the untouched 184 byte packet, read against fixed values. */
    @Test
    void stillDecodesTheHundredAndEightyFourBytePacketExactly() {
        Tick tick = parseOne(Packets.full184());

        assertEquals(Packets.EQUITY_TOKEN, tick.getInstrumentToken());
        assertTrue(tick.isTradable());
        assertEquals(KiteTicker.modeFull, tick.getMode());
        assertEquals(1425.50d, tick.getLastTradedPrice(), DELTA);
        assertEquals(37d, tick.getLastTradedQuantity(), DELTA);
        assertEquals(1423.10d, tick.getAverageTradePrice(), DELTA);
        assertEquals(8123456L, tick.getVolumeTradedToday());
        assertEquals(410200d, tick.getTotalBuyQuantity(), DELTA);
        assertEquals(398100d, tick.getTotalSellQuantity(), DELTA);
        assertEquals(1419.00d, tick.getOpenPrice(), DELTA);
        assertEquals(1432.20d, tick.getHighPrice(), DELTA);
        assertEquals(1410.10d, tick.getLowPrice(), DELTA);
        assertEquals(1417.50d, tick.getClosePrice(), DELTA);
        assertEquals((1425.50d - 1417.50d) * 100 / 1417.50d, tick.getChange(), DELTA);
        assertEquals(Packets.LAST_TRADED_TIME_SECONDS * 1000L, tick.getLastTradedTime().getTime());
        assertEquals(Packets.TICK_TIME_SECONDS * 1000L, tick.getTickTimestamp().getTime());
        assertEquals(2750d, tick.getOi(), DELTA);
        assertEquals(3100d, tick.getOpenInterestDayHigh(), DELTA);
        assertEquals(2400d, tick.getOpenInterestDayLow(), DELTA);

        List<Depth> buy = tick.getMarketDepth().get("buy");
        List<Depth> sell = tick.getMarketDepth().get("sell");
        assertEquals(5, buy.size());
        assertEquals(5, sell.size());
        assertEquals(100, buy.get(0).getQuantity());
        assertEquals(1425.00d, buy.get(0).getPrice(), DELTA);
        assertEquals(5, buy.get(0).getOrders());
        assertEquals(109, sell.get(4).getQuantity());
        assertEquals(1425.09d, sell.get(4).getPrice(), DELTA);
        assertEquals(14, sell.get(4).getOrders());
    }

    /** A stream mixing the old and the new packet sizes splits and decodes both. */
    @Test
    void splitsAStreamThatMixesPacketSizes() {
        ArrayList<Tick> ticks = ticker.parseBinary(
                Packets.frame(Packets.index36(2343580), Packets.full184(), Packets.fullCas200(142600, 142480, -500L)));

        assertEquals(3, ticks.size());
        assertEquals(KiteTicker.modeFullCAS, ticks.get(0).getMode());
        assertEquals(KiteTicker.modeFull, ticks.get(1).getMode());
        assertEquals(KiteTicker.modeFullCAS, ticks.get(2).getMode());
        assertEquals(-500L, ticks.get(2).getTotalImbalanceQty());
    }

    private static void assertDepthEquals(Tick expected, Tick actual) {
        for (String side : new String[]{"buy", "sell"}) {
            List<Depth> a = expected.getMarketDepth().get(side);
            List<Depth> b = actual.getMarketDepth().get(side);
            assertEquals(a.size(), b.size(), side + " depth size");
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).getQuantity(), b.get(i).getQuantity(), side + " qty " + i);
                assertEquals(a.get(i).getPrice(), b.get(i).getPrice(), DELTA, side + " price " + i);
                assertEquals(a.get(i).getOrders(), b.get(i).getOrders(), side + " orders " + i);
            }
        }
    }
}
