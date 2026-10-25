package spireledger;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.*;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import java.util.*;

final class Snapshot {
    static final Map<UUID,String> identities=new HashMap<>();
    static JsonObject card(AbstractCard c){
        JsonObject o=new JsonObject();o.addProperty("id",c.cardID);
        o.addProperty("instance_id",identities.computeIfAbsent(c.uuid,u->u.toString()));
        o.addProperty("name",c.name);o.addProperty("type",c.type.name());o.addProperty("upgrades",c.timesUpgraded);
        o.addProperty("misc",c.misc);o.addProperty("base_damage",c.baseDamage);o.addProperty("base_block",c.baseBlock);o.addProperty("base_magic",c.baseMagicNumber);o.addProperty("base_cost",c.cost);
        if(c.inBottleFlame)o.addProperty("bottle_flame",true);if(c.inBottleLightning)o.addProperty("bottle_lightning",true);if(c.inBottleTornado)o.addProperty("bottle_tornado",true);
        return o;
    }
    static JsonObject capture(){
        JsonObject o=new JsonObject();o.addProperty("act",AbstractDungeon.actNum);o.addProperty("floor",AbstractDungeon.floorNum);
        o.addProperty("class",AbstractDungeon.player.chosenClass.name());o.addProperty("ascension",AbstractDungeon.ascensionLevel);
        o.addProperty("is_daily",Settings.isDailyRun);o.addProperty("is_trial",Settings.isTrial);o.addProperty("is_endless",Settings.isEndless);
        o.addProperty("hp",AbstractDungeon.player.currentHealth);o.addProperty("max_hp",AbstractDungeon.player.maxHealth);o.addProperty("gold",AbstractDungeon.player.gold);
        // Vanilla counter: only gainGold increments it; resetScoreVars zeroes it; loadPlayerSave restores SaveFile.gold_gained.
        o.addProperty("gold_gained",CardCrawlGame.goldGained);
        o.addProperty("resource_timing","direct_game_thread_at_record");
        JsonArray deck=new JsonArray();for(AbstractCard c:AbstractDungeon.player.masterDeck.group)deck.add(card(c));o.add("deck",deck);
        JsonArray relics=new JsonArray();for(AbstractRelic r:AbstractDungeon.player.relics){JsonObject item=new JsonObject();item.addProperty("id",r.relicId);item.addProperty("counter",r.counter);relics.add(item);}o.add("relics",relics);
        JsonArray potions=new JsonArray();for(AbstractPotion p:AbstractDungeon.player.potions){JsonObject item=new JsonObject();item.addProperty("id",p.ID);item.addProperty("slot",p.slot);potions.add(item);}o.add("potions",potions);
        AbstractRoom room=AbstractDungeon.getCurrRoom();o.addProperty("room",room==null?"unknown":room.getClass().getSimpleName());o.addProperty("room_phase",room==null?"unknown":room.phase.name());
        if(room!=null&&room.event!=null)o.addProperty("event",room.event.getClass().getSimpleName());
        MapRoomNode n=AbstractDungeon.getCurrMapNode();if(n!=null){o.addProperty("x",n.x);o.addProperty("y",n.y);}
        o.addProperty("ruby",Settings.hasRubyKey);o.addProperty("emerald",Settings.hasEmeraldKey);o.addProperty("sapphire",Settings.hasSapphireKey);
        return o;
    }
    static String buildSignature(JsonObject o){return o.get("deck").toString()+o.get("relics").toString();}
    static String source(boolean rewardItem){
        AbstractRoom r=AbstractDungeon.getCurrRoom();
        if(AbstractDungeon.floorNum==0||r instanceof NeowRoom)return "NEOW";
        if(r!=null&&r.phase==AbstractRoom.RoomPhase.COMBAT)return "COMBAT_SELECTION";
        if(!rewardItem)return r!=null&&r.event!=null?"EVENT":"OTHER";
        if(r instanceof MonsterRoom)return "COMBAT_REWARD";
        // Event combat rewards have actual monsters and the native reward screen.
        if(r!=null&&r.event!=null&&r.monsters!=null&&!r.monsters.monsters.isEmpty()&&r.phase==AbstractRoom.RoomPhase.COMPLETE)return "COMBAT_REWARD";
        return r!=null&&r.event!=null?"EVENT":"OTHER";
    }
    static boolean settled(){
        AbstractRoom r=AbstractDungeon.getCurrRoom();
        return r!=null&&r.phase!=AbstractRoom.RoomPhase.COMBAT&&AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID&&AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.HAND_SELECT
            &&!blockingEffects(AbstractDungeon.effectsQueue)&&!blockingEffects(AbstractDungeon.effectList)&&!blockingEffects(AbstractDungeon.topLevelEffects)&&!blockingEffects(AbstractDungeon.topLevelEffectsQueue)
            &&!SoulGroup.isActive()&&(AbstractDungeon.actionManager==null||(AbstractDungeon.actionManager.actions.isEmpty()&&AbstractDungeon.actionManager.currentAction==null));
    }
    static boolean blockingEffects(List<AbstractGameEffect> effects){
        // Original scene and rare-potion sparkles respawn without mutating builds/resources.
        // The precise potion class was verified after Tiny House stalled the native fixture.
        // All other live effects, including mod subclasses, continue to block settlement.
        for(AbstractGameEffect e:effects)if(!e.isDone&&!e.getClass().getName().startsWith("com.megacrit.cardcrawl.vfx.scene.")&&!e.getClass().getName().equals("com.megacrit.cardcrawl.vfx.RarePotionParticleEffect"))return true;
        return false;
    }
    static JsonArray map(){JsonArray all=new JsonArray();if(AbstractDungeon.map!=null)for(ArrayList<MapRoomNode> row:AbstractDungeon.map)for(MapRoomNode n:row)if(n.getRoom()!=null){JsonObject o=new JsonObject();o.addProperty("x",n.x);o.addProperty("y",n.y);o.addProperty("room",n.getRoom().getClass().getSimpleName());o.addProperty("burning",n.hasEmeraldKey);JsonArray edges=new JsonArray();for(MapEdge edge:n.getEdges()){JsonArray pair=new JsonArray();pair.add(edge.dstX);pair.add(edge.dstY);edges.add(pair);}o.add("edges",edges);all.add(o);}return all;}
    static void restoreIdentities(JsonArray saved){
        identities.clear();if(saved==null||saved.size()!=AbstractDungeon.player.masterDeck.group.size())return;
        for(int i=0;i<saved.size();i++){AbstractCard c=AbstractDungeon.player.masterDeck.group.get(i);JsonObject old=saved.get(i).getAsJsonObject();
            if(!c.cardID.equals(Recorder.string(old,"id"))||c.timesUpgraded!=old.get("upgrades").getAsInt()||c.misc!=old.get("misc").getAsInt())return;}
        for(int i=0;i<saved.size();i++)identities.put(AbstractDungeon.player.masterDeck.group.get(i).uuid,Recorder.string(saved.get(i).getAsJsonObject(),"instance_id"));
    }
}
