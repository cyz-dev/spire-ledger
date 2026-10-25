package spireledger;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.ui.buttons.SingingBowlButton;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.ui.buttons.ProceedButton;
import com.megacrit.cardcrawl.ui.buttons.SkipCardButton;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.cards.CardGroup;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.shop.*;

public final class Hooks {
    @SpirePatch(clz=AbstractPlayer.class,method="preBattlePrep") public static class CombatIdentity {
        @SpirePrefixPatch public static void before(){TurnRecorder.battle();}
    }
    @SpirePatch(clz=AbstractPlayer.class,method="updateInput") public static class TurnReady {
        @SpirePrefixPatch public static void before(){TurnRecorder.observe();}
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.actions.GameActionManager.class,method="update") public static class TurnActionsSettled {
        @SpirePostfixPatch public static void after(){TurnRecorder.observe();}
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.actions.GameActionManager.class,method="addCardQueueItem",paramtypez={com.megacrit.cardcrawl.cards.CardQueueItem.class}) public static class BeforeQueuedCard {
        @SpirePrefixPatch public static void before(){TurnRecorder.observe();}
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.metrics.Metrics.class,method="gatherAllDataAndSave",paramtypez={boolean.class,boolean.class,MonsterGroup.class}) public static class PlayId {
        @SpirePrefixPatch public static void before(com.megacrit.cardcrawl.metrics.Metrics __instance){StateMod.metricsStarted(__instance);}
        @SpirePostfixPatch public static void after(com.megacrit.cardcrawl.metrics.Metrics __instance,boolean death,boolean trueVictor){StateMod.metricsFinished(__instance,death,trueVictor);}
    }
    @SpirePatch(clz=CardRewardScreen.class,method="acquireCard") public static class Card {
        @SpirePrefixPatch public static void before(CardRewardScreen __instance,AbstractCard hoveredCard){StateMod.choose(__instance,hoveredCard);}
    }
    // Discovery/Neow stores its selection, then its owning event obtains it on a later update.
    @SpirePatch(clz=CardRewardScreen.class,method="takeReward") public static class Discovery {
        @SpirePrefixPatch public static void before(CardRewardScreen __instance){if(__instance.discoveryCard!=null)StateMod.choose(__instance,__instance.discoveryCard);}
    }
    @SpirePatch(clz=RewardItem.class,method="recordCardSkipMetrics") public static class Skip {
        @SpirePrefixPatch public static void before(RewardItem __instance){StateMod.skip(__instance);}
    }
    @SpirePatch(clz=CardRewardScreen.class,method="skippedCards") public static class NoncombatSkip {
        @SpirePrefixPatch public static void before(CardRewardScreen __instance){StateMod.screenSkip(__instance);}
    }
    @SpirePatch(clz=SkipCardButton.class,method="update") public static class NoncombatSkipClick {
        @SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(MethodCall c)throws CannotCompileException {
            if(c.getClassName().equals("com.megacrit.cardcrawl.dungeons.AbstractDungeon")&&c.getMethodName().equals("closeCurrentScreen"))c.replace("{ spireledger.Hooks.noncombatSkip(); $proceed($$); }");
        }};}
    }
    public static void noncombatSkip(){if(com.megacrit.cardcrawl.dungeons.AbstractDungeon.screen==com.megacrit.cardcrawl.dungeons.AbstractDungeon.CurrentScreen.CARD_REWARD)StateMod.screenSkip(com.megacrit.cardcrawl.dungeons.AbstractDungeon.cardRewardScreen);}
    @SpirePatch(clz=SingingBowlButton.class,method="onClick") public static class Bowl {
        @SpirePrefixPatch public static void before(SingingBowlButton __instance){StateMod.bowl(__instance);}
    }
    // Boss/event reward exits do not all invoke vanilla skip metrics. The actual proceed close
    // is the commit boundary; temporary CardRewardScreen/peek closes are deliberately untouched.
    @SpirePatch(clz=ProceedButton.class,method="update") public static class FinalAbandon {
        @SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(MethodCall c)throws CannotCompileException {
            if(c.getClassName().equals("com.megacrit.cardcrawl.dungeons.AbstractDungeon")&&c.getMethodName().equals("closeCurrentScreen"))c.replace("{ spireledger.Hooks.abandon(); $proceed($$); }");
        }};}
    }
    public static void abandon(){StateMod.abandon();}
    @SpirePatch(clz=ProceedButton.class,method="goToTreasureRoom") public static class BossExit { @SpirePrefixPatch public static void before(){StateMod.abandon();} }
    @SpirePatch(clz=ProceedButton.class,method="goToVictoryRoomOrTheDoor") public static class Act3Exit { @SpirePrefixPatch public static void before(){StateMod.abandon();} }
    @SpirePatch(clz=ProceedButton.class,method="goToDoubleBoss") public static class DoubleBossExit { @SpirePrefixPatch public static void before(){StateMod.abandon();} }
    @SpirePatch(clz=ProceedButton.class,method="goToTrueVictoryRoom") public static class HeartExit { @SpirePrefixPatch public static void before(){StateMod.abandon();} }
    @SpirePatch(clz=NeowEvent.class,method="buttonEffect") public static class NeowOption {
        @SpirePrefixPatch public static void before(NeowEvent __instance,int buttonPressed){StateMod.neow(__instance,buttonPressed);}
    }
    @SpirePatch(clz=ShopScreen.class,method="open") public static class ShopOpen {
        @SpirePostfixPatch public static void after(ShopScreen __instance){StateMod.shopOffer(__instance);}
    }
    // Purchase prefixes mirror each method's own affordability guard, so a refused click is not a choice.
    @SpirePatch(clz=ShopScreen.class,method="purchaseCard") public static class ShopCard {
        @SpirePrefixPatch public static void before(ShopScreen __instance,AbstractCard hoveredCard){if(AbstractDungeon.player.gold>=hoveredCard.price)StateMod.shopBuy(hoveredCard,"card:"+Snapshot.id(hoveredCard));}
    }
    @SpirePatch(clz=StoreRelic.class,method="purchaseRelic") public static class ShopRelic {
        @SpirePrefixPatch public static void before(StoreRelic __instance){if(AbstractDungeon.player.gold>=__instance.price)StateMod.shopBuy(__instance.relic,"relic:"+StateMod.field(__instance,"slot"));}
    }
    @SpirePatch(clz=StorePotion.class,method="purchasePotion") public static class ShopPotion {
        @SpirePrefixPatch public static void before(StorePotion __instance){if(Snapshot.canBuyPotion(__instance.price))StateMod.shopBuy(__instance.potion,"potion:"+StateMod.field(__instance,"slot"));}
    }
    // purgeCard runs only after the removal grid returned a card; purchasePurge already checked gold.
    @SpirePatch(clz=ShopScreen.class,method="purgeCard") public static class ShopPurge {
        @SpirePrefixPatch public static void before(){StateMod.shopBuy(null,"purge");}
    }
    @SpirePatch(clz=AbstractDungeon.class,method="nextRoomTransition",paramtypez={SaveFile.class}) public static class RoomExit {
        @SpirePrefixPatch public static void before(){StateMod.leaveRoom();}
    }
    // Entry sample: floorNum is already advanced; relic onEnterRoom/justEnteredRoom and room onPlayerEntry have not run.
    @SpirePatch(clz=AbstractDungeon.class,method="nextRoomTransition",paramtypez={SaveFile.class}) public static class RoomEntry {
        @SpireInsertPatch(locator=Locator.class) public static void before(){StateMod.enterRoom();}
        static class Locator extends SpireInsertLocator {public int[] Locate(CtBehavior m)throws Exception{return LineFinder.findInOrder(m,new Matcher.MethodCallMatcher(StatsScreen.class,"incrementFloorClimbed"));}}
    }
    @SpirePatch(clz=GridCardSelectScreen.class,method="open",paramtypez={CardGroup.class,int.class,String.class,boolean.class,boolean.class,boolean.class,boolean.class}) public static class Grid {
        @SpirePrefixPatch public static void before(){StateMod.mutation("grid_selection; pending until effects settle");}
    }
    @SpirePatch(clz=GridCardSelectScreen.class,method="open",paramtypez={CardGroup.class,int.class,boolean.class,String.class}) public static class GridSimple {
        @SpirePrefixPatch public static void before(){StateMod.mutation("grid_selection; pending until effects settle");}
    }
    @SpirePatch(clz=AbstractRelic.class,method="obtain") public static class Relic {
        @SpirePrefixPatch public static void before(AbstractRelic __instance){StateMod.mutation("relic:"+__instance.relicId);}
    }
    @SpirePatch(clz=AbstractRelic.class,method="instantObtain",paramtypez={}) public static class RelicInstant {
        @SpirePrefixPatch public static void before(AbstractRelic __instance){StateMod.mutation("relic:"+__instance.relicId);}
    }
    @SpirePatch(clz=AbstractRelic.class,method="instantObtain",paramtypez={AbstractPlayer.class,int.class,boolean.class}) public static class RelicPlayer {
        @SpirePrefixPatch public static void before(AbstractRelic __instance){StateMod.mutation("relic:"+__instance.relicId);}
    }
    @SpirePatch(clz=DeathScreen.class,method=SpirePatch.CONSTRUCTOR,paramtypez={MonsterGroup.class}) public static class Death {
        @SpirePostfixPatch public static void after(){StateMod.end("death");}
    }
    @SpirePatch(clz=VictoryScreen.class,method=SpirePatch.CONSTRUCTOR,paramtypez={MonsterGroup.class}) public static class Victory {
        @SpirePostfixPatch public static void after(){StateMod.end("victory");}
    }
}
