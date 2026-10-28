package spireledger;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.*;
import java.lang.reflect.Method;

/** The solver (0.9.6 on) owns its queues; invalidate its old plan before changing a battle. */
public final class SolverReplay {
    static Boolean contract;
    /** Every solver member this class drives or patches; a release that renames one is not a supported replay environment. */
    static synchronized boolean contract(){
        if(contract==null)try{Class<?> type=Class.forName("sts1solver.SolverMod");
            type.getField("instance");type.getDeclaredMethod("cancel",boolean.class);type.getDeclaredMethod("stopAll");type.getDeclaredField("runAutoPlayer");
            type.getDeclaredMethod("receivePostUpdate");type.getDeclaredMethod("receivePostRender",com.badlogic.gdx.graphics.g2d.SpriteBatch.class);contract=true;
        }catch(ReflectiveOperationException|LinkageError ex){System.err.println("[Spire Ledger] Solver replay contract missing: "+ex);contract=false;}
        return contract;
    }
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
