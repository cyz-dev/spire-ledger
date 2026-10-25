package sts1state;

import com.google.gson.*;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import java.lang.reflect.*;
import java.util.*;

/** The single tested SaveState 1.3 path. No bundled third-party implementation. */
final class TurnAdapter {
    static final String ID="state-notes-savestate-1.3-capture-only-2";
    static final String[] POOLS={"srcColorlessCardPool","srcCurseCardPool","srcCommonCardPool","srcUncommonCardPool","srcRareCardPool","colorlessCardPool","curseCardPool","commonCardPool","uncommonCardPool","rareCardPool"};
    static int checkedInt(JsonElement value){if(value==null||!value.isJsonPrimitive())throw new IllegalArgumentException("missing integer");return Integer.parseInt(value.getAsString());}
    static JsonObject environment()throws Exception{
        JsonObject out=new JsonObject();out.addProperty("dungeon_id",AbstractDungeon.id);out.addProperty("act",AbstractDungeon.actNum);
        out.addProperty("class",AbstractDungeon.player.chosenClass.name());out.addProperty("seed",String.valueOf(Settings.seed));
        out.addProperty("ascension",AbstractDungeon.ascensionLevel);out.addProperty("ascension_mode",AbstractDungeon.isAscensionMode);
        JsonObject pools=new JsonObject();Class<?> type=Class.forName("savestate.CardState");
        for(String name:POOLS){JsonArray cards=new JsonArray();CardGroup group=(CardGroup)AbstractDungeon.class.getField(name).get(null);
            for(AbstractCard card:group.group)cards.add(encode(call(type,"forCard",new Class<?>[]{AbstractCard.class},card)));pools.add(name,cards);}
        out.add("card_pools",pools);return out;
    }
    static void restoreEnvironment(JsonObject env)throws Exception{
        Settings.seed=Long.valueOf(env.get("seed").getAsString());AbstractDungeon.actNum=env.get("act").getAsInt();AbstractDungeon.id=env.get("dungeon_id").getAsString();
        AbstractDungeon.ascensionLevel=env.get("ascension").getAsInt();AbstractDungeon.isAscensionMode=env.get("ascension_mode").getAsBoolean();
        Class<?> type=Class.forName("savestate.CardState");
        for(String name:POOLS){CardGroup pool=(CardGroup)AbstractDungeon.class.getField(name).get(null);pool.clear();
            for(JsonElement card:env.getAsJsonObject("card_pools").getAsJsonArray(name))pool.group.add((AbstractCard)call(call(type,"forJson",new Class<?>[]{JsonObject.class},card.getAsJsonObject()),"loadCard",new Class<?>[0]));}
    }
    static final String[] RNGS={"monsterRng","mapRng","eventRng","merchantRng","cardRng","treasureRng","relicRng","potionRng","monsterHpRng","aiRng","shuffleRng","cardRandomRng","miscRng"};
    static final String[] COUNTERS={"damageReceivedThisTurn","damageReceivedThisCombat","hpLossThisCombat","playerHpLastTurn","energyGainedThisCombat"};
    static final Set<String> COSMETICS=new HashSet<>(Arrays.asList("com.megacrit.cardcrawl.vfx.stance.StanceAuraEffect","com.megacrit.cardcrawl.vfx.stance.CalmParticleEffect","com.megacrit.cardcrawl.vfx.stance.WrathParticleEffect","com.megacrit.cardcrawl.vfx.stance.DivinityParticleEffect","com.megacrit.cardcrawl.vfx.combat.DarkOrbPassiveEffect","com.megacrit.cardcrawl.vfx.combat.LightningOrbPassiveEffect","com.megacrit.cardcrawl.vfx.combat.FrostOrbPassiveEffect","com.megacrit.cardcrawl.vfx.combat.PlasmaOrbPassiveEffect"));
    static boolean effects(List<AbstractGameEffect> effects){List<AbstractGameEffect> rest=new ArrayList<>();for(AbstractGameEffect e:effects)if(!COSMETICS.contains(e.getClass().getName()))rest.add(e);return Snapshot.blockingEffects(rest);}
    static boolean stable(){return !HistoryScreen.active()&&restoreReady();}
    static boolean restoreReady(){if(!StateMod.ready())return false;GameActionManager a=AbstractDungeon.actionManager;return a!=null&&AbstractDungeon.getCurrRoom().phase==AbstractRoom.RoomPhase.COMBAT&&!AbstractDungeon.isScreenUp&&a.currentAction==null&&a.turnStartCurrentAction==null&&a.actions.isEmpty()&&a.preTurnActions.isEmpty()&&a.cardQueue.isEmpty()&&a.monsterQueue.isEmpty()&&!a.turnHasEnded&&!AbstractDungeon.player.isEndingTurn&&!effects(AbstractDungeon.effectList)&&!effects(AbstractDungeon.topLevelEffects)&&!effects(AbstractDungeon.effectsQueue)&&!effects(AbstractDungeon.topLevelEffectsQueue)&&a.phase==GameActionManager.Phase.WAITING_ON_USER&&AbstractDungeon.overlayMenu.endTurnButton.enabled;}
    static Object call(Object target,String method,Class<?>[] types,Object... args)throws Exception{return (target instanceof Class?(Class<?>)target:target.getClass()).getMethod(method,types).invoke(target instanceof Class?null:target,args);}
    static Class<?> saver()throws ClassNotFoundException{return Class.forName("savestate.SaveState");}
    static JsonObject encode(Object value)throws Exception{return (JsonObject)call(value,"jsonEncode",new Class<?>[0]);}
    static List<AbstractCard> allCards(){List<AbstractCard> cards=new ArrayList<>();for(CardGroup pile:Arrays.asList(AbstractDungeon.player.masterDeck,AbstractDungeon.player.drawPile,AbstractDungeon.player.hand,AbstractDungeon.player.discardPile,AbstractDungeon.player.exhaustPile,AbstractDungeon.player.limbo))cards.addAll(pile.group);return cards;}
    static JsonArray cards(List<AbstractCard> played)throws Exception{JsonArray entries=new JsonArray();List<AbstractCard> all=allCards();Class<?> type=Class.forName("savestate.CardState");for(AbstractCard c:played){JsonObject e=new JsonObject();int i=all.indexOf(c);e.addProperty("index",i);if(i<0)e.add("card",encode(call(type,"forCard",new Class<?>[]{AbstractCard.class},c)));entries.add(e);}return entries;}
    static void loadCards(JsonArray entries,List<AbstractCard> target)throws Exception{target.clear();List<AbstractCard> all=allCards();Class<?> type=Class.forName("savestate.CardState");for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();int i=e.get("index").getAsInt();if(i>=all.size()||i< -1)throw new IllegalArgumentException("card reference bounds");target.add(i>=0?all.get(i):(AbstractCard)call(call(type,"forJson",new Class<?>[]{JsonObject.class},e.getAsJsonObject("card")),"loadCard",new Class<?>[0]));}}
    static JsonArray orbs(List<AbstractOrb> played)throws Exception{JsonArray entries=new JsonArray();Class<?> type=Class.forName("savestate.orbs.OrbState");for(AbstractOrb orb:played){JsonObject e=new JsonObject();e.addProperty("active_index",AbstractDungeon.player.orbs.indexOf(orb));e.addProperty("combat_index",AbstractDungeon.actionManager.orbsChanneledThisCombat.indexOf(orb));e.add("orb",encode(call(type,"forOrb",new Class<?>[]{AbstractOrb.class},orb)));entries.add(e);}return entries;}
    static void loadOrbs(JsonArray entries,List<AbstractOrb> target,boolean turn)throws Exception{target.clear();Class<?> type=Class.forName("savestate.orbs.OrbState");for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();int active=e.get("active_index").getAsInt(),combat=e.get("combat_index").getAsInt();target.add(active>=0?AbstractDungeon.player.orbs.get(active):turn&&combat>=0?AbstractDungeon.actionManager.orbsChanneledThisCombat.get(combat):(AbstractOrb)call(call(type,"forJsonObject",new Class<?>[]{JsonObject.class},e.getAsJsonObject("orb")),"loadOrb",new Class<?>[0]));}}
    static JsonObject rng()throws Exception{JsonObject result=new JsonObject();for(String name:RNGS){com.megacrit.cardcrawl.random.Random r=(com.megacrit.cardcrawl.random.Random)AbstractDungeon.class.getField(name).get(null);if(r==null)continue;JsonObject v=new JsonObject();v.addProperty("counter",r.counter);v.addProperty("state0",String.valueOf(r.random.getState(0)));v.addProperty("state1",String.valueOf(r.random.getState(1)));result.add(name,v);}return result;}
    static JsonObject supplement()throws Exception{GameActionManager a=AbstractDungeon.actionManager;JsonObject out=new JsonObject();out.add("rng",rng());out.add("combat_history",cards(a.cardsPlayedThisCombat));out.add("turn_history",cards(a.cardsPlayedThisTurn));out.add("drawn",cards(com.megacrit.cardcrawl.actions.common.DrawCardAction.drawnCards));JsonObject counters=new JsonObject();for(String key:COUNTERS)counters.addProperty(key,GameActionManager.class.getField(key).getInt(null));out.add("counters",counters);JsonObject manager=new JsonObject();manager.add("unique_stances",new Gson().toJsonTree(a.uniqueStancesThisCombat));manager.add("orb_combat_history",orbs(a.orbsChanneledThisCombat));manager.add("orb_turn_history",orbs(a.orbsChanneledThisTurn));manager.add("last_card",a.lastCard==null?JsonNull.INSTANCE:cards(Collections.singletonList(a.lastCard)).get(0));out.add("manager",manager);JsonObject counts=new JsonObject();for(String name:Arrays.asList("CountLessonLearnedHitsPatch","CountParasitesPatch"))counts.addProperty(name,Class.forName("savestate.SaveState$"+name).getField("count").getInt(null));out.add("save_state_counts",counts);return out;}
        static void repair(JsonObject extra)throws Exception{
        loadCards(extra.getAsJsonArray("combat_history"),AbstractDungeon.actionManager.cardsPlayedThisCombat);
        loadCards(extra.getAsJsonArray("turn_history"),AbstractDungeon.actionManager.cardsPlayedThisTurn);
        loadCards(extra.getAsJsonArray("drawn"),com.megacrit.cardcrawl.actions.common.DrawCardAction.drawnCards);
        for(String key:COUNTERS)GameActionManager.class.getField(key).setInt(null,extra.getAsJsonObject("counters").get(key).getAsInt());
        JsonObject m=extra.getAsJsonObject("manager");
        GameActionManager a=AbstractDungeon.actionManager;
        a.uniqueStancesThisCombat.clear();
        for(Map.Entry<String,JsonElement> e:m.getAsJsonObject("unique_stances").entrySet())a.uniqueStancesThisCombat.put(e.getKey(),e.getValue().getAsInt());
        loadOrbs(m.getAsJsonArray("orb_combat_history"),a.orbsChanneledThisCombat,false);
        loadOrbs(m.getAsJsonArray("orb_turn_history"),a.orbsChanneledThisTurn,true);
        if(m.get("last_card").isJsonNull())a.lastCard=null;
        else{
            JsonArray entries=new JsonArray();
            entries.add(m.get("last_card"));
            List<AbstractCard> cards=new ArrayList<>();
            loadCards(entries,cards);
            a.lastCard=cards.get(0);
        }
        if(extra.has("save_state_counts")&&extra.get("save_state_counts").isJsonObject()){
            for(Map.Entry<String,JsonElement> e:extra.getAsJsonObject("save_state_counts").entrySet())Class.forName("savestate.SaveState$"+e.getKey()).getField("count").setInt(null,e.getValue().getAsInt());
        }
        for(Map.Entry<String,JsonElement> e:extra.getAsJsonObject("rng").entrySet()){
            JsonObject v=e.getValue().getAsJsonObject();
            com.megacrit.cardcrawl.random.Random r=(com.megacrit.cardcrawl.random.Random)AbstractDungeon.class.getField(e.getKey()).get(null);
            r.random.setState(Long.parseLong(v.get("state0").getAsString()), Long.parseLong(v.get("state1").getAsString()));
            r.counter=v.get("counter").getAsInt();
        }
    }
static JsonObject capture()throws Exception{JsonObject before=rng(),out=new JsonObject();out.addProperty("adapter",ID);out.add("state",encode(saver().getConstructor().newInstance()));out.add("supplement",supplement());out.add("environment",environment());if(!before.equals(rng()))throw new IllegalStateException("snapshot changed RNG");JsonObject creature=out.getAsJsonObject("state").getAsJsonObject("player_state").getAsJsonObject("creature");creature.addProperty("name",AbstractDungeon.player.chosenClass.name());return out;}
        static void validateSnapshot(JsonObject snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Snapshot is null");
        if (!ID.equals(HistoryIndex.str(snapshot, "adapter"))) throw new IllegalArgumentException("snapshot adapter mismatch");
        JsonObject env=snapshot.getAsJsonObject("environment");
        if(env==null)throw new IllegalArgumentException("missing environment");
        String dungeon=env.get("dungeon_id").getAsString();int act=checkedInt(env.get("act"));
        if(act<1||act>4||!Arrays.asList("Exordium","TheCity","TheBeyond","TheEnding").get(act-1).equals(dungeon))throw new IllegalArgumentException("dungeon/act mismatch");
        com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass.valueOf(env.get("class").getAsString());Long.parseLong(env.get("seed").getAsString());
        int asc=checkedInt(env.get("ascension"));if(asc<0||asc>20)throw new IllegalArgumentException("ascension bounds");env.get("ascension_mode").getAsBoolean();
        JsonObject pools=env.getAsJsonObject("card_pools");if(pools==null)throw new IllegalArgumentException("missing card pools");
        for(String name:POOLS){JsonArray pool=pools.getAsJsonArray(name);if(pool==null||pool.size()>1000)throw new IllegalArgumentException("missing/oversized card pool: "+name);for(JsonElement card:pool)if(!card.isJsonObject()||!card.getAsJsonObject().has("card_id"))throw new IllegalArgumentException("invalid pool card");}
        if (!snapshot.has("state") || !snapshot.get("state").isJsonObject()) throw new IllegalArgumentException("missing or invalid state payload");
        JsonObject state = snapshot.getAsJsonObject("state");
        if (!state.has("player_state") || !state.has("rng_state") || !state.has("cur_map_node_state")) {
            throw new IllegalArgumentException("corrupted state payload: missing core sections");
        }
        if(!env.get("class").getAsString().equals(state.getAsJsonObject("player_state").get("chosen_class_name").getAsString())||env.get("seed").getAsLong()!=state.getAsJsonObject("rng_state").get("seed").getAsLong()||env.get("ascension").getAsInt()!=state.get("ascension_level").getAsInt())throw new IllegalArgumentException("snapshot environment/state mismatch");
        if (!snapshot.has("supplement") || !snapshot.get("supplement").isJsonObject()) throw new IllegalArgumentException("missing or invalid supplement payload");
        JsonObject supp = snapshot.getAsJsonObject("supplement");
        if (!supp.has("rng") || !supp.get("rng").isJsonObject()) throw new IllegalArgumentException("corrupted supplement payload: missing or invalid rng");
        JsonObject suppRng = supp.getAsJsonObject("rng");
        for (String rName : RNGS) {
            if (!suppRng.has(rName) || !suppRng.get(rName).isJsonObject()) throw new IllegalArgumentException("corrupted supplement rng: missing " + rName);
            JsonObject rObj = suppRng.getAsJsonObject(rName);
            if (!rObj.has("counter") || !rObj.has("state0") || !rObj.has("state1")) throw new IllegalArgumentException("incomplete rng state fields in " + rName);
            if(checkedInt(rObj.get("counter"))<0)throw new IllegalArgumentException("negative RNG counter");Long.parseLong(rObj.get("state0").getAsString());Long.parseLong(rObj.get("state1").getAsString());
        }

        JsonObject playerState = state.getAsJsonObject("player_state");
        int totalCards = 0;
        String[] cardPiles = {"master_deck", "draw_pile", "hand", "discard_pile", "exhaust_pile", "limbo"};
        for (String pile : cardPiles) {
            if (playerState.has(pile) && playerState.get(pile).isJsonArray()) {
                totalCards += playerState.getAsJsonArray(pile).size();
            }
        }
        int maxActiveOrbs = playerState.has("orbs") && playerState.get("orbs").isJsonArray() ? playerState.getAsJsonArray("orbs").size() : 0;
        int maxCombatOrbs = playerState.has("orbs_channeled_this_combat") && playerState.get("orbs_channeled_this_combat").isJsonArray() ? playerState.getAsJsonArray("orbs_channeled_this_combat").size() : 0;

        if (!supp.has("combat_history") || !supp.get("combat_history").isJsonArray()) throw new IllegalArgumentException("corrupted supplement payload: missing or invalid combat_history");
        if (!supp.has("turn_history") || !supp.get("turn_history").isJsonArray()) throw new IllegalArgumentException("corrupted supplement payload: missing or invalid turn_history");
        if (!supp.has("drawn") || !supp.get("drawn").isJsonArray()) throw new IllegalArgumentException("corrupted supplement payload: missing or invalid drawn");
        if (!supp.has("counters") || !supp.get("counters").isJsonObject()) throw new IllegalArgumentException("corrupted supplement payload: missing or invalid counters");
        JsonObject counters = supp.getAsJsonObject("counters");
        for (String c : COUNTERS) {
            if (!counters.has(c) || !counters.get(c).isJsonPrimitive()) throw new IllegalArgumentException("corrupted supplement counters: missing " + c);
            checkedInt(counters.get(c));
        }
        if (!supp.has("manager") || !supp.get("manager").isJsonObject()) throw new IllegalArgumentException("corrupted supplement payload: missing or invalid manager");
        JsonObject mgr = supp.getAsJsonObject("manager");
        if (!mgr.has("unique_stances") || !mgr.get("unique_stances").isJsonObject()) throw new IllegalArgumentException("corrupted supplement manager: missing unique_stances");
        if (!mgr.has("orb_combat_history") || !mgr.get("orb_combat_history").isJsonArray()) throw new IllegalArgumentException("corrupted supplement manager: missing orb_combat_history");
        if (!mgr.has("orb_turn_history") || !mgr.get("orb_turn_history").isJsonArray()) throw new IllegalArgumentException("corrupted supplement manager: missing orb_turn_history");
        if (!mgr.has("last_card")) throw new IllegalArgumentException("corrupted supplement manager: missing last_card");
        validateCardRefs(supp.getAsJsonArray("combat_history"), totalCards, "combat_history");
        validateCardRefs(supp.getAsJsonArray("turn_history"), totalCards, "turn_history");
        validateCardRefs(supp.getAsJsonArray("drawn"), totalCards, "drawn");
        if (!mgr.get("last_card").isJsonNull()) {
            if (!mgr.get("last_card").isJsonObject()) throw new IllegalArgumentException("corrupted manager last_card element");
            validateCardRef(mgr.getAsJsonObject("last_card"), totalCards, "last_card");
        }
        validateOrbRefs(mgr.getAsJsonArray("orb_combat_history"), maxActiveOrbs, maxCombatOrbs, "orb_combat_history");
        validateOrbRefs(mgr.getAsJsonArray("orb_turn_history"), maxActiveOrbs, maxCombatOrbs, "orb_turn_history");
    }
    static void validateCardRefs(JsonArray array, int maxCards, String context) {
        for (JsonElement e : array) {
            if (!e.isJsonObject()) throw new IllegalArgumentException("invalid card reference in " + context);
            validateCardRef(e.getAsJsonObject(), maxCards, context);
        }
    }
    static void validateCardRef(JsonObject obj, int maxCards, String context) {
        if (!obj.has("index") || !obj.get("index").isJsonPrimitive()) throw new IllegalArgumentException("missing index in " + context);
        int idx = checkedInt(obj.get("index"));
        if (idx < -1 || idx >= maxCards) throw new IllegalArgumentException("out-of-bounds card reference in " + context + ": index=" + idx + ", max=" + maxCards);
        if (idx == -1 && (!obj.has("card") || !obj.get("card").isJsonObject())) throw new IllegalArgumentException("missing fallback card payload in " + context);
    }
    static void validateOrbRefs(JsonArray array, int maxActive, int maxCombat, String context) {
        for (JsonElement e : array) {
            if (!e.isJsonObject()) throw new IllegalArgumentException("invalid orb reference in " + context);
            JsonObject obj = e.getAsJsonObject();
            if (!obj.has("active_index") || !obj.has("combat_index")) throw new IllegalArgumentException("missing orb indices in " + context);
            int active = checkedInt(obj.get("active_index"));
            int combat = checkedInt(obj.get("combat_index"));
            if (active < -1 || active >= maxActive) throw new IllegalArgumentException("out-of-bounds active orb index in " + context + ": active=" + active + ", max=" + maxActive);
            if (combat < -1 || combat >= maxCombat) throw new IllegalArgumentException("out-of-bounds combat orb index in " + context + ": combat=" + combat + ", max=" + maxCombat);
            if (active == -1 && combat == -1 && (!obj.has("orb") || !obj.get("orb").isJsonObject())) throw new IllegalArgumentException("missing fallback orb payload in " + context);
        }
    }
    static void readyAfterRestore(){
        AbstractDungeon.effectList.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.topLevelEffectsQueue.clear();
        AbstractDungeon.isScreenUp=false;AbstractDungeon.screen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.previousScreen=null;
        GameActionManager a=AbstractDungeon.actionManager;
        a.actions.clear();a.preTurnActions.clear();a.cardQueue.clear();a.monsterQueue.clear();a.currentAction=null;a.turnStartCurrentAction=null;a.turnHasEnded=false;a.phase=GameActionManager.Phase.WAITING_ON_USER;
        AbstractDungeon.overlayMenu.showCombatPanels();AbstractDungeon.overlayMenu.endTurnButton.enable();
        AbstractDungeon.player.isEndingTurn=false;AbstractDungeon.player.hand.refreshHandLayout();AbstractDungeon.player.hand.applyPowers();AbstractDungeon.player.hand.glowCheck();
    }
    static final Set<String> VISUAL_FIELDS=new HashSet<>(Arrays.asList("inspect_hb","hb","health_hb","intent_hb","hb_x","hb_y","hb_w","hb_h","hb_alpha","draw_x","draw_y","dialog_x","dialog_y","anim_x","anim_y","reticle_alpha","reticle_rendered","intent_alpha","intent_alpha_target","intent_offset_x","previous_screen_name"));
    static JsonElement logical(JsonElement value){
        if(value.isJsonArray()){JsonArray out=new JsonArray();for(JsonElement e:value.getAsJsonArray())out.add(logical(e));return out;}
        if(!value.isJsonObject())return value;
        JsonObject out=new JsonObject();for(Map.Entry<String,JsonElement> e:value.getAsJsonObject().entrySet())if(!VISUAL_FIELDS.contains(e.getKey()))out.add(e.getKey(),logical(e.getValue()));
        if("Potion Slot".equals(Recorder.string(out,"id")))out.remove("slot");
        if(out.has("wait_timer")&&out.get("wait_timer").getAsDouble()<=0)out.addProperty("wait_timer",0);
        JsonObject creature=out.getAsJsonObject("creature");if(creature!=null&&creature.has("is_dead")&&creature.get("is_dead").getAsBoolean()&&out.has("death_timer")&&out.get("death_timer").getAsDouble()<0)out.addProperty("death_timer",0);
        return out;
    }
    static void load(JsonObject snapshot)throws Exception{
        Object saved=saver().getConstructor(JsonObject.class).newInstance(snapshot.getAsJsonObject("state"));
        call(saved,"loadState",new Class<?>[0]);restoreEnvironment(snapshot.getAsJsonObject("environment"));repair(snapshot.getAsJsonObject("supplement"));readyAfterRestore();
    }
    static void restore(JsonObject snapshot)throws Exception{
        if(ReplaySafety.quarantined)throw new IllegalStateException("Game is frozen after a failed restore; restart this process");
        validateSnapshot(snapshot);DiagnosticReport.rememberReplay(snapshot);
        if(!ReplaySafety.practice()&&!restoreReady())throw new IllegalStateException("Wait for an idle player turn before replay");
        boolean liveCombat=AbstractDungeon.actionManager!=null&&AbstractDungeon.currMapNode!=null&&AbstractDungeon.getCurrRoom()!=null&&AbstractDungeon.getCurrRoom().phase==AbstractRoom.RoomPhase.COMBAT;
        // Capture must complete before the first destructive call. Failure leaves the live game untouched.
        JsonObject backup=liveCombat?capture():null;
        if(backup==null&&!ReplaySafety.practice())throw new IllegalStateException("No restorable live combat backup");
        try{load(snapshot);}catch(Throwable failure){DiagnosticReport.error("restore",failure);
            if(backup!=null){
                try{load(backup);JsonObject restored=capture();if(!backup.get("supplement").equals(restored.get("supplement"))||!backup.get("environment").equals(restored.get("environment"))||!logical(backup.get("state")).equals(logical(restored.get("state"))))throw new IllegalStateException("Rollback verification mismatch");}catch(Throwable rollback){failure.addSuppressed(rollback);ReplaySafety.quarantine(failure);}
            }else ReplaySafety.quarantine(failure);
            if(failure instanceof Exception)throw (Exception)failure;
            throw new IllegalStateException("Restore failed",failure);
        }
    }
}
