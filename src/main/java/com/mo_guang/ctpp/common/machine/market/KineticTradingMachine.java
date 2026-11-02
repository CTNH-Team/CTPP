package com.mo_guang.ctpp.common.machine.market;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import com.mo_guang.ctpp.common.machine.IKineticMachine;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;
import java.util.Locale;

import javax.annotation.ParametersAreNonnullByDefault;

/** Trading machine driven by Create rotation; each settled trade bills the network. */
@Prefix("market")
@Category("machine")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class KineticTradingMachine extends TradingMachine implements IKineticMachine {

    @CN("接入动力网络时持续施加应力")
    @EN("Applies stress for as long as the network turns it")
    private static Lang kinetic_trading_tooltip;

    @CN("§a应力消耗：§f%s su/RPM")
    @EN("§aStress Impact: §f%s su/RPM")
    private static Lang stress_impact;

    @CN("§a转速需求：§f> 0 RPM")
    @EN("§aSpeed Required: §f> 0 RPM")
    private static Lang speed_required;

    /** Shaft speed that yields the base trade rate. */
    private static final float NOMINAL_RPM = 256.0f;

    public KineticTradingMachine(IMachineBlockEntity holder) {
        super(holder, com.gregtechceu.gtceu.api.GTValues.LV);
    }

    /** Only the back face carries a shaft; the default rule would put one through the shopfront. */
    @Override
    public boolean hasShaftTowards(Direction face) {
        return face == getRotationFacing().getOpposite();
    }

    /** Requires a live, turning network; the inherited EU check would always fail. */
    @Override
    protected boolean canTrade() {
        var holder = getKineticHolder();
        return holder.hasNetwork() && holder.getSpeed() != 0.0f;
    }

    @Override
    protected TradeExecutor.Result powerFailure() {
        return TradeExecutor.Result.NO_STRESS;
    }

    /**
     * Trade rate follows shaft speed, so gearing the shop faster is how you make it faster. 256 RPM is treated as
     * the reference speed; below that it trades proportionally slower.
     */
    @Override
    protected double tradesPerTick() {
        float speed = Math.abs(getKineticHolder().getSpeed());
        return TRADES_PER_TICK * Math.max(0.0f, speed / NOMINAL_RPM);
    }

    /**
     * A kinetic shop has no EU buffer, so the inherited EU line would read as a stack of zeroes. Report the stress
     * it costs instead, in the same shape GT uses for its own energy lines.
     */
    @Override
    protected List<Component> energyTooltip() {
        // The impact comes from the machine definition, which is what Create actually bills the network for; a
        // constant here would drift from the configured value as soon as anyone changed it.
        return List.of(
                stress_impact.translate(String.format(Locale.ROOT, "%.0f", stressImpact())),
                speed_required.translate());
    }

    /** The stress this machine adds to a network, before Create scales it by the shaft speed. */
    private float stressImpact() {
        return getKineticDefinition().getTorque();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(kinetic_trading_tooltip.translate());
        return true;
    }
}
