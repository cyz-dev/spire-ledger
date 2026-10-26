package spireledger;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.*;
import java.lang.reflect.Method;

/** Solver 0.9.6 owns its queues; invalidate its old plan before changing a battle. */
public final class SolverReplay {
    static void beforeRestore() throws Exception {
        if(!Loader.isModLoaded("sts1solver"))return;
        Class<?> type=Class.forName("sts1solver.SolverMod");Object solver=type.getField("instance").get(null);
        if(solver==null)throw new IllegalStateException("Solver is not initialized");
        // cancel(false) advances the reply generation and discards results; F9 then leaves all automation stopped.
        Method cancel=type.getDeclaredMethod("cancel",boolean.class);cancel.setAccessible(true);cancel.invoke(solver,false);
        Method stop=type.getDeclaredMethod("stopAll");stop.setAccessible(true);stop.invoke(solver);
    }
    static void afterRestore() throws Exception {
        if(!Loader.isModLoaded("sts1solver"))return;
        Class<?> type=Class.forName("sts1solver.SolverMod");Object solver=type.getField("instance").get(null);
        java.lang.reflect.Field player=type.getDeclaredField("runAutoPlayer");player.setAccessible(true);player.set(solver,com.megacrit.cardcrawl.dungeons.AbstractDungeon.player);
    }
    @SpirePatch(cls="sts1solver.SolverMod",method="receivePostUpdate",requiredModId="sts1solver",optional=true)
    public static class Pause {
        @SpirePrefixPatch public static SpireReturn<Void> before(){return HistoryScreen.active()||ReplaySafety.quarantined?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(cls="sts1solver.SolverMod",method="receivePostRender",requiredModId="sts1solver",optional=true)
    public static class Hide {
        @SpirePrefixPatch public static SpireReturn<Void> before(){return HistoryScreen.active()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
}
