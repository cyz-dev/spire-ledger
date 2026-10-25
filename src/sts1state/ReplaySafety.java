package sts1state;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.integrations.*;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import javassist.CannotCompileException;
import javassist.expr.*;

/** A failed rollback is terminal for this process, even if a UI catches the exception. */
public final class ReplaySafety {
    static boolean quarantined;
    static boolean practice(){return Boolean.getBoolean("sts1state.practiceMode");}
    static void quarantine(Throwable failure){
        quarantined=true;DiagnosticReport.error("restore_quarantine",failure);
        System.err.println("[State Notes] Restore failed without a verified rollback. Gameplay and saving are frozen; close this game process and reload the untouched native save.");
        failure.printStackTrace();
    }
    @SpirePatch(clz=CardCrawlGame.class,method="update") public static class Freeze {
        @SpirePrefixPatch public static SpireReturn<Void> before(){return quarantined?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(clz=CardCrawlGame.class,method="render") public static class FrozenScreen {
        @SpirePrefixPatch public static SpireReturn<Void> before(CardCrawlGame game){
            DiagnosticReport.pollShortcut();if(!quarantined)return SpireReturn.Continue();
            com.badlogic.gdx.graphics.g2d.SpriteBatch ___sb=(com.badlogic.gdx.graphics.g2d.SpriteBatch)StateMod.field(game,"sb");
            com.badlogic.gdx.Gdx.gl.glClearColor(0,0,0,1);com.badlogic.gdx.Gdx.gl.glClear(16384);
            ___sb.begin();
            com.megacrit.cardcrawl.helpers.FontHelper.renderFontCentered(___sb,com.megacrit.cardcrawl.helpers.FontHelper.buttonLabelFont,HistoryScreen.tr("恢复失败，游戏已冻结。","Restore failed; gameplay is frozen."),com.megacrit.cardcrawl.core.Settings.WIDTH/2f,com.megacrit.cardcrawl.core.Settings.HEIGHT/2f,com.badlogic.gdx.graphics.Color.WHITE);
            com.megacrit.cardcrawl.helpers.FontHelper.renderFontCentered(___sb,com.megacrit.cardcrawl.helpers.FontHelper.buttonLabelFont,HistoryScreen.tr("请关闭游戏，重新读取原存档。","Close this game process and reload the native save."),com.megacrit.cardcrawl.core.Settings.WIDTH/2f,com.megacrit.cardcrawl.core.Settings.HEIGHT/2f-50,com.badlogic.gdx.graphics.Color.WHITE);
            com.megacrit.cardcrawl.helpers.FontHelper.renderFontCentered(___sb,com.megacrit.cardcrawl.helpers.FontHelper.cardDescFont_N,"Ctrl+Shift+R · ~/.sts1-state-notes/bug-reports",com.megacrit.cardcrawl.core.Settings.WIDTH/2f,com.megacrit.cardcrawl.core.Settings.HEIGHT/2f-100,com.badlogic.gdx.graphics.Color.WHITE);
            DiagnosticReport.draw(___sb);___sb.end();return SpireReturn.Return(null);
        }
    }
    @SpirePatch(clz=CardCrawlGame.class,method="render") public static class ReportStatus {
        @SpirePostfixPatch public static void after(CardCrawlGame game){if(quarantined||DiagnosticReport.status.isEmpty())return;com.badlogic.gdx.graphics.g2d.SpriteBatch sb=(com.badlogic.gdx.graphics.g2d.SpriteBatch)StateMod.field(game,"sb");sb.begin();DiagnosticReport.draw(sb);sb.end();}
    }
    @SpirePatch(clz=com.megacrit.cardcrawl.metrics.Metrics.class,method="run") public static class NoMetricsUpload {
        @SpirePrefixPatch public static SpireReturn<Void> before(){return quarantined||practice()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(clz=SaveAndContinue.class,method="save") public static class NoSave {
        @SpirePrefixPatch public static SpireReturn<Void> before(){return quarantined||practice()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(clz=SaveAndContinue.class,method="deleteSave") public static class NoDelete {
        @SpirePrefixPatch public static SpireReturn<Void> before(){return quarantined||practice()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {
        @SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(){return practice()?SpireReturn.Return(new GogIntegration()):SpireReturn.Continue();}
    }
    @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {
        @SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{
            if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = sts1state.ReplaySafety.isPractice() ? null : $proceed($$); }");
        }};}
    }
    public static boolean isPractice(){return practice();}
}
