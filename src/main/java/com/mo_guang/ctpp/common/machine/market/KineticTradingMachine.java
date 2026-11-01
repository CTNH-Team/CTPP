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

    @CN("运行中会向动力网络施加应力")
    @EN("Applies stress to the kinetic network while trading")
    private static Lang kinetic_trading_tooltip;

    @CN("§a应力消耗：§f%s su")
    @EN("§aStress Impact: §f%s su")
    private static Lang stress_impact;

    @CN("§a转速需求：§f> 0 RPM")
    @EN("§aSpeed Required: §f> 0 RPM")
    private static Lang speed_required;

    /** Stress (su) charged to the network while a trade is in flight. */
    private static final float STRESS_PER_TRADE = 64.0f;
    /** How many ticks the stress stays applied after a trade settles. */
    private static final int STRESS_DURATION = 40;

    private int stressTicks;

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
     * A kinetic shop has no EU buffer, so the inherited EU line would read as a stack of zeroes. Report the stress
     * it costs instead, in the same shape GT uses for its own energy lines.
     */
    @Override
    protected List<Component> energyTooltip() {
        return List.of(
                stress_impact.translate(String.format(Locale.ROOT, "%.0f", STRESS_PER_TRADE)),
                speed_required.translate());
    }

    @Override
    protected void afterTrade() {
        // Deliberately no super call: a kinetic shop is driven by rotation, not by the EU buffer.
        stressTicks = STRESS_DURATION;
        updateStress(true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            subscribeServerTick(this::tickStress);
        }
    }

    private void tickStress() {
        if (stressTicks > 0 && --stressTicks == 0) {
            updateStress(false);
        }
    }

    private void updateStress(boolean active) {
        var holder = getKineticHolder();
        if (holder.hasNetwork()) {
            holder.getOrCreateNetwork().updateStressFor(holder, active ? STRESS_PER_TRADE : 0.0f);
            holder.getOrCreateNetwork().updateStress();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(kinetic_trading_tooltip.translate());
        return true;
    }
}
