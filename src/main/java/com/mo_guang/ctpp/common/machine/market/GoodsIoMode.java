package com.mo_guang.ctpp.common.machine.market;

/**
 * Behaviour of one goods channel (items or fluids) on its selected face.
 * <p>
 * The three values deliberately line up with GT's own io-config sprite sheet, which draws off / output / auto
 * thirds. {@link #AUTO_OUTPUT} keeps the machine's behaviour (push the internal stock out of that face) and
 * {@link #PROXY} replaces GT's third position with something the market needs: trade straight against the
 * neighbour's inventory, so a shop can be fed from a warehouse without the goods ever entering the machine.
 */
public enum GoodsIoMode {

    /** No goods handling; the face is not the selected one for this channel. */
    NONE,
    /** Push the machine's own goods out of the selected face. */
    AUTO_OUTPUT,
    /** Trade directly against the neighbouring inventory instead of the machine's own goods area. */
    PROXY;

    private static final GoodsIoMode[] CYCLE = { NONE, AUTO_OUTPUT, PROXY };

    /** The next mode in the click cycle: none → auto output → proxy → none. */
    public GoodsIoMode next() {
        return CYCLE[(ordinal() + 1) % CYCLE.length];
    }

    /** Colour of the face highlight, matching GT's orange for item io and blue for fluid io. */
    public int highlightColour(boolean fluid) {
        return switch (this) {
            case NONE -> 0x00000000;
            case AUTO_OUTPUT -> fluid ? 0x8f00b4ff : 0xffff6e0f;
            case PROXY -> fluid ? 0xff00b4ff : 0xff20b2ff;
        };
    }
}
