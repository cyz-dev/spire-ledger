package sts1state;

import basemod.ModPanel;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.screens.mainMenu.*;

public final class HistoryHooks {
    @SpireEnum public static MenuButton.ClickResult STATE_NOTES;
    @SpirePatch(clz=MainMenuScreen.class,method="setMainMenuButtons") public static class Entry {
        @SpirePostfixPatch public static void add(MainMenuScreen menu){try{java.lang.reflect.Field index=MenuButton.class.getDeclaredField("index");index.setAccessible(true);for(MenuButton b:menu.buttons){index.setInt(b,index.getInt(b)+1);b.hb.moveY(b.hb.cY+MenuButton.SPACE_Y);}menu.buttons.add(0,new MenuButton(STATE_NOTES,0));}catch(Exception e){throw new IllegalStateException(e);}}
    }
    @SpirePatch(clz=MenuButton.class,method=SpirePatch.CONSTRUCTOR,paramtypez={MenuButton.ClickResult.class,int.class}) public static class Label {
        @SpirePostfixPatch public static void label(MenuButton button,MenuButton.ClickResult r){if(r!=STATE_NOTES)return;try{java.lang.reflect.Field f=MenuButton.class.getDeclaredField("label");f.setAccessible(true);String label=HistoryScreen.tr("构筑记录","State Notes");f.set(button,label);button.hb.resize(FontHelper.getSmartWidth(FontHelper.buttonLabelFont,label,9999,1)+100*com.megacrit.cardcrawl.core.Settings.scale,button.hb.height);button.hb.move(button.hb.width/2+75*com.megacrit.cardcrawl.core.Settings.scale,button.hb.cY);}catch(Exception e){throw new IllegalStateException(e);}}
    }
    @SpirePatch(clz=MenuButton.class,method="buttonEffect") public static class Open {
        @SpirePrefixPatch public static SpireReturn<Void> open(MenuButton b){if(b.result==STATE_NOTES){HistoryScreen.open();return SpireReturn.Return(null);}return SpireReturn.Continue();}
    }
    @SpirePatch(clz=MainMenuScreen.class,method="update") public static class MenuUpdate {
        @SpirePrefixPatch public static SpireReturn<Void> update(){if(HistoryScreen.active()){HistoryScreen.instance.update();return SpireReturn.Return(null);}return SpireReturn.Continue();}
    }
    @SpirePatch(clz=MainMenuScreen.class,method="render") public static class MenuRender {
        @SpirePrefixPatch public static SpireReturn<Void> render(MainMenuScreen m,SpriteBatch sb){if(HistoryScreen.active()){HistoryScreen.instance.render(sb);return SpireReturn.Return(null);}return SpireReturn.Continue();}
    }
    @SpirePatch(clz=AbstractDungeon.class,method="update") public static class DungeonUpdate {
        @SpirePrefixPatch public static SpireReturn<Void> update(){if(HistoryScreen.active()){HistoryScreen.instance.update();return SpireReturn.Return(null);}return SpireReturn.Continue();}
    }
    @SpirePatch(clz=AbstractDungeon.class,method="render") public static class DungeonRender {
        @SpirePrefixPatch public static SpireReturn<Void> render(AbstractDungeon d,SpriteBatch sb){if(HistoryScreen.active()){HistoryScreen.instance.render(sb);return SpireReturn.Return(null);}return SpireReturn.Continue();}
    }
    @SpirePatch(clz=ModPanel.class,method="update") public static class PanelUpdate {
        @SpirePrefixPatch public static SpireReturn<Void> update(){return HistoryScreen.active()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(clz=ModPanel.class,method="render") public static class PanelRender {
        @SpirePrefixPatch public static SpireReturn<Void> render(){return HistoryScreen.active()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
    @SpirePatch(clz=ModPanel.class,method="renderBg") public static class PanelBackground {
        @SpirePrefixPatch public static SpireReturn<Void> render(){return HistoryScreen.active()?SpireReturn.Return(null):SpireReturn.Continue();}
    }
}
