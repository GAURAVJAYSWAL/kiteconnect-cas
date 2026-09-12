package com.zerodhatech.ticker;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds the synthetic byte arrays the parser tests feed in. Framing mirrors what the ticker
 * writes on the wire: a two byte packet count, then for every packet a two byte length followed
 * by the payload.
 */
final class Packets {

    /** RELIANCE. The low byte is 1, so the segment resolves to NseCM and the divisor to 100. */
    static final int EQUITY_TOKEN = 738561;

    /** NIFTY 50. The low byte is 9, so the segment resolves to Indices and the tick is untradable. */
    static final int INDEX_TOKEN = 256265;

    private Packets() {
    }

    static byte[] frame(byte[]... payloads) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(shortBytes(payloads.length), 0, 2);
        for (byte[] payload : payloads) {
            out.write(shortBytes(payload.length), 0, 2);
            out.write(payload, 0, payload.length);
        }
        return out.toByteArray();
    }

    /**
     * The 184 byte full packet: quote block, then last traded time, open interest, the tick
     * timestamp, and a ten level depth book of twelve bytes each.
     */
    static byte[] full184() {
        ByteBuffer bb = ByteBuffer.allocate(184).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(EQUITY_TOKEN);
        bb.putInt(142550);      // last traded price 1425.50
        bb.putInt(37);          // last traded quantity
        bb.putInt(142310);      // average traded price 1423.10
        bb.putInt(8123456);     // volume
        bb.putInt(410200);      // total buy quantity
        bb.putInt(398100);      // total sell quantity
        bb.putInt(141900);      // open 1419.00
        bb.putInt(143220);      // high 1432.20
        bb.putInt(141010);      // low 1410.10
        bb.putInt(141750);      // close 1417.50
        bb.putInt(LAST_TRADED_TIME_SECONDS);
        bb.putInt(2750);        // open interest
        bb.putInt(3100);        // open interest day high
        bb.putInt(2400);        // open interest day low
        bb.putInt(TICK_TIME_SECONDS);
        for (int level = 0; level < 10; level++) {
            bb.putInt(100 + level);                     // quantity
            bb.putInt(142500 + (level < 5 ? -level : level)); // price
            bb.putShort((short) (5 + level));           // orders
            bb.putShort((short) 0);                     // padding
        }
        return bb.array();
    }

    /** The 200 byte call auction packet: the same 184 bytes with the auction block appended. */
    static byte[] fullCas200(int referenceLimitPriceRaw, int indicativeClosePriceRaw, long imbalance) {
        byte[] base = full184();
        ByteBuffer bb = ByteBuffer.allocate(200).order(ByteOrder.BIG_ENDIAN);
        bb.put(base);
        bb.putInt(referenceLimitPriceRaw);
        bb.putInt(indicativeClosePriceRaw);
        bb.putLong(imbalance);
        return bb.array();
    }

    /** The 32 byte index packet the library already understood. */
    static byte[] index32() {
        return indexBytes(32, 0);
    }

    /** The 36 byte index packet: the 32 byte one with the indicative close appended. */
    static byte[] index36(int indicativeClosePriceRaw) {
        return indexBytes(36, indicativeClosePriceRaw);
    }

    private static byte[] indexBytes(int length, int indicativeClosePriceRaw) {
        ByteBuffer bb = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(INDEX_TOKEN);
        bb.putInt(2343510);     // last traded price 23435.10
        bb.putInt(2351000);     // high 23510.00
        bb.putInt(2331000);     // low 23310.00
        bb.putInt(2340000);     // open 23400.00
        bb.putInt(2338000);     // close 23380.00
        bb.putInt(5510);        // absolute change, the parser recomputes it as a percentage
        bb.putInt(TICK_TIME_SECONDS);
        if (length >= 36) {
            bb.putInt(indicativeClosePriceRaw);
        }
        return bb.array();
    }

    static final int LAST_TRADED_TIME_SECONDS = 1757668500; // 2025-09-12 15:25:00 IST
    static final int TICK_TIME_SECONDS = 1757668501;

    private static byte[] shortBytes(int value) {
        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) value).array();
    }
}
