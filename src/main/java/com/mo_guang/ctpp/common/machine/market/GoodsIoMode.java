package com.mo_guang.ctpp.common.machine.market;

/**
 * Per-face goods behaviour, matching the three thirds of GT's io-config sprites. {@link #PROXY} takes the place
 * of GT's auto slot: it trades against the neighbouring inventory instead of the machine's own.
 */
public enum GoodsIoMode {

    /** Not the selected face for this channel. */
    NONE,
    /** Push the machine's own goods out of the selected face. */
    AUTO_OUTPUT,
    /** Trade directly against the neighbouring inventory instead of the machine's own goods area. */
    PROXY;

    private static final GoodsIoMode[] CYCLE = { NONE, AUTO_OUTPUT, PROXY };

    /** Next mode in the click cycle. */
    public GoodsIoMode next() {
        return CYCLE[(ordinal() + 1) % CYCLE.length];
    }

    /** Face highlight colour. */
    public int highlightColour(boolean fluid) {
        return switch (this) {
            case NONE -> 0x00000000;
            case AUTO_OUTPUT -> fluid ? 0x8f00b4ff : 0xffff6e0f;
            case PROXY -> fluid ? 0xff00b4ff : 0xff20b2ff;
        };
    }
}
