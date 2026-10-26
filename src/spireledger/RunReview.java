package spireledger;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import java.text.SimpleDateFormat;
import java.util.*;

/** Run overview with hoverable route nodes. Only reads recorded JSON and library textures. */
final class RunReview {
    final HistoryScreen host;
    RunReviewModel model;JsonObject document;int branch=-1,hover=-1,scroll;boolean pinned;
    final List<Hitbox> nodes=new ArrayList<>();
    final Hitbox back=new Hitbox(1,1);
    final Hitbox overview=new Hitbox(1,1),deckTab=new Hitbox(1,1),choiceTab=new Hitbox(1,1);
    boolean showDeck;
    static final Color PANEL=new Color(.09f,.12f,.16f,1),GOLD=new Color(.91f,.75f,.38f,1),MUTED=HistoryScreen.MUTED;
    RunReview(HistoryScreen host){this.host=host;}
    void reset(){model=null;document=null;branch=-1;hover=-1;scroll=0;pinned=false;showDeck=false;}
    static String tr(String cn,String en){return HistoryScreen.tr(cn,en);}
    void sync(){
        if(host.detail==null)return;
        if(document==host.detail&&branch==host.branchSelection)return;
        document=host.detail;branch=host.branchSelection;model=new RunReviewModel(host.viewRecords);nodes.clear();
        Map<Integer,List<Integer>> acts=new LinkedHashMap<>();
        for(int i=0;i<model.floors.size();i++)acts.computeIfAbsent(model.floors.get(i).act,a->new ArrayList<>()).add(i);
        for(int i=0;i<model.floors.size();i++)nodes.add(new Hitbox(1,1));
        int row=0;for(List<Integer> floors:acts.values()){
            float spacing=Math.min(w(.043f),w(.75f)/Math.max(1,floors.size())),y=h(.745f-row*.071f);
            for(int col=0;col<floors.size();col++){Hitbox hb=nodes.get(floors.get(col));hb.resize(spacing*.88f,h(.059f));hb.move(w(.205f)+col*spacing,y);}
            row++;
        }
        back.resize(w(.14f),h(.037f));back.move(w(.12f),h(.075f));
        overview.resize(w(.16f),h(.035f));overview.move(w(.86f),h(.39f));
        choiceTab.resize(w(.13f),h(.035f));choiceTab.move(w(.13f),h(.39f));
        deckTab.resize(w(.18f),h(.035f));deckTab.move(w(.30f),h(.39f));
        host.prev.move(w(.32f),h(.075f));host.next.move(w(.46f),h(.075f));host.diagnostics.move(w(.65f),h(.075f));host.replayButton.move(w(.85f),h(.075f));
    }
    static float w(float x){return Settings.WIDTH*x;}static float h(float y){return Settings.HEIGHT*y;}
    int focused(){return pinned?indexForFloor():hover;}
    int indexForFloor(){if(host.floorOrder.isEmpty())return -1;int floor=host.floorOrder.get(host.floorIndex);for(int i=0;i<model.floors.size();i++)if(model.floors.get(i).number==floor)return i;return -1;}
    void choose(int i){int number=model.floors.get(i).number;int position=host.floorOrder.indexOf(number);if(position>=0){host.floorIndex=position;host.focusFloor();host.makeLines();}scroll=0;}
    void update(){
        sync();if(model==null)return;
        if(pinned&&host.turnMenu!=null){host.turnMenu.update();if(host.turnMenu.isOpen)return;}
        int over=-1;
        for(int i=0;i<nodes.size();i++){Hitbox node=nodes.get(i);boolean clicked=host.click(node);if(node.hovered)over=i;if(clicked){pinned=true;showDeck=false;choose(i);}}
        if(over!=hover){hover=over;if(!pinned){scroll=0;if(over>=0)choose(over);}}
        if(host.click(overview)){pinned=false;hover=-1;scroll=0;showDeck=false;}
        if(pinned&&host.click(choiceTab)){showDeck=false;scroll=0;}
        if(pinned&&host.click(deckTab)){showDeck=true;scroll=0;}
        if(host.click(host.prev)&&!model.floors.isEmpty()){int i=Math.max(0,indexForFloor()-1);pinned=true;choose(i);}
        if(host.click(host.next)&&!model.floors.isEmpty()){int i=Math.min(model.floors.size()-1,indexForFloor()+1);pinned=true;choose(i);}
        if(host.click(host.diagnostics)){host.raw=true;host.makeLines();return;}
        if(pinned&&!host.availableTurns.isEmpty()&&host.click(host.replayButton))host.replaySelected();
        if(InputHelper.scrolledDown)scroll++;if(InputHelper.scrolledUp)scroll--;scroll=Math.max(0,Math.min(maxScroll(),scroll));
        List<Hitbox> controls=new ArrayList<>(nodes);if(pinned)controls.addAll(Arrays.asList(overview,choiceTab,deckTab));controls.addAll(Arrays.asList(host.prev,host.next,host.diagnostics,host.latestTab,host.historyTab,host.home,back));if(pinned&&!host.availableTurns.isEmpty())controls.add(host.replayButton);host.controller(controls);
    }
    int maxScroll(){int f=focused();if(f<0||showDeck){JsonObject state=f<0?model.finalState:model.floors.get(f).last;return Math.max(0,(RunReviewModel.deck(state).size()+3)/4-5);}return Math.max(0,entries(model.floors.get(f)).size()-5);}
    void text(SpriteBatch sb,String text,float x,float y,Color color){host.text(sb,text,w(x),h(y),color);}
    void rect(SpriteBatch sb,float x,float y,float width,float height,Color color){sb.setColor(color);sb.draw(ImageMaster.WHITE_SQUARE_IMG,w(x),h(y),w(width),h(height));sb.setColor(Color.WHITE);}
    void render(SpriteBatch sb){
        sync();if(model==null){text(sb,tr("正在读取对局…","Reading run…"),.06f,.8f,MUTED);return;}
        String title=host.outcome(host.selected.outcome)+"  ·  "+HistoryScreen.className(host.selected.character)+"  A"+host.selected.ascension;
        FontHelper.renderFontLeftTopAligned(sb,FontHelper.buttonLabelFont,title,w(.06f),h(.89f),GOLD);
        text(sb,host.resources(model.finalState)+tr("   牌组 ","   Deck ")+HistoryIndex.array(model.finalState,"deck").size(),.06f,.835f,Settings.CREAM_COLOR);
        String date=host.selected.time>0?new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(host.selected.time)):"";
        text(sb,host.fit(date+tr("  种子 ","  Seed ")+host.selected.seed,w(.51f)),.45f,.89f,MUTED);
        int focus=focused();Set<Integer> acts=new HashSet<>();
        for(int i=0;i<model.floors.size();i++){
            RunReviewModel.Floor floor=model.floors.get(i);Hitbox node=nodes.get(i);
            if(acts.add(floor.act))host.text(sb,tr("第 ","Act ")+floor.act+tr(" 幕",""),w(.07f),node.cY+h(.013f),MUTED);
            if(i==focus)host.box(sb,node,new Color(.25f,.23f,.15f,1));
            Texture icon=roomIcon(floor.room);Color color=roomColor(floor.room);float size=Math.min(node.width*.8f,h(.041f));
            if(icon!=null){sb.setColor(color);sb.draw(icon,node.cX-size/2,node.cY-size*.35f,size,size);sb.setColor(Color.WHITE);}
            else FontHelper.renderFontCentered(sb,FontHelper.cardDescFont_N,floor.number==0?"N":floor.room.contains("Victory")?"V":"?",node.cX,node.cY+h(.01f),color);
            FontHelper.renderFontCentered(sb,FontHelper.cardDescFont_N,String.valueOf(floor.number),node.cX,node.y+h(.006f),i==focus?GOLD:MUTED);
        }
        text(sb,tr("悬停楼层查看 · 点击固定详情与重打","Hover a floor to inspect · Click to pin and replay"),.06f,.455f,MUTED);
        rect(sb,.055f,.425f,.89f,.002f,new Color(.3f,.28f,.2f,1));
        if(focus<0)renderOverview(sb,model.finalState);
        else renderFloor(sb,model.floors.get(focus));
        if(!host.message.isEmpty())text(sb,host.fit(host.message,w(.74f)),.20f,.115f,Color.SALMON);
        host.button(sb,back,tr("关闭账本","Close"));host.button(sb,host.prev,tr("上一层","Previous"));host.button(sb,host.next,tr("下一层","Next"));host.button(sb,host.diagnostics,tr("详细记录","Details"));
        if(pinned&&!host.availableTurns.isEmpty()){
            host.button(sb,host.replayButton,ReplayLauncher.canReplayCurrent(host.availableTurns.get(host.turnSelection))?tr("重打此回合","Restart turn"):tr("练习此回合","Practice turn"));
            host.turnMenu.render(sb,w(.70f),h(.18f));
        }
    }
    void renderOverview(SpriteBatch sb,JsonObject state){
        text(sb,tr("遗物","Relics")+"  "+HistoryIndex.array(state,"relics").size(),.06f,.395f,GOLD);
        renderRelics(sb,state,.15f,.383f,.79f);
        text(sb,tr("最终牌组","Final deck")+"  "+HistoryIndex.array(state,"deck").size(),.06f,.333f,GOLD);
        renderDeck(sb,state,.30f);
    }
    void renderFloor(SpriteBatch sb,RunReviewModel.Floor floor){
        String room=HistoryScreen.roomName(floor.room,HistoryScreen.zh());if(room==null)room=floor.room.isEmpty()?tr("房间未记录","Room not recorded"):floor.room;
        String title=tr("第 ","Floor ")+floor.number+tr(" 层 · "," · ")+room;
        text(sb,title,.06f,.395f,GOLD);
        if(pinned){host.button(sb,overview,tr("返回整局总览","Run overview"));choiceTab.move(w(.55f),h(.39f));deckTab.move(w(.70f),h(.39f));host.button(sb,choiceTab,tr("选择与变化","Choices"));host.button(sb,deckTab,tr("本层牌组","Deck here"));}
        text(sb,tr("记录时：","Observed: ")+observedResources(floor),.06f,.348f,Settings.CREAM_COLOR);
        if(showDeck){renderDeck(sb,floor.last,.30f);return;}
        List<Entry> rows=entries(floor);int limit=5;
        for(int i=0;i<limit&&scroll+i<rows.size();i++){
            Entry row=rows.get(scroll+i);float y=.30f-i*.029f;
            if(row.card!=null)drawCard(sb,row.card,.06f,y-.001f,.028f,.028f);
            text(sb,host.fit(row.label,w(.84f)),row.card==null?.06f:.10f,y,row.color);
        }
        if(rows.size()>limit)text(sb,tr("滚轮查看 ","Scroll ")+(scroll+1)+"–"+Math.min(rows.size(),scroll+limit)+" / "+rows.size(),.69f,.32f,MUTED);
        if(pinned){
            String status=!host.availableTurns.isEmpty()?tr("已保存 ","Saved ")+host.availableTurns.size()+tr(" 个回合。重打前会停止求解器。"," turns. Replay stops the solver first."):tr("本层未保存可重打快照。旧记录无法补录。","No saved turn here. Past turns cannot be reconstructed.");
            text(sb,host.fit(status,w(.65f)),.20f,.115f,MUTED);
        }
    }
    String observedResources(RunReviewModel.Floor floor){StringBuilder s=new StringBuilder();for(String key:Arrays.asList("hp","gold")){if(s.length()>0)s.append("    ");s.append(key.equals("hp")?tr("生命 ","HP "):tr("金币 ","Gold "));String before=host.value(floor.first,key),after=host.value(floor.last,key);s.append(before);if(!before.equals(after))s.append(" → ").append(after);}return s.toString();}
    static final class Entry {final String label;final JsonObject card;final Color color;Entry(String label,JsonObject card,Color color){this.label=label;this.card=card;this.color=color;}}
    List<Entry> entries(RunReviewModel.Floor floor){
        List<Entry> rows=new ArrayList<>();
        for(JsonObject decision:floor.decisions){
            String chosen=HistoryIndex.str(decision,"candidate_id");boolean confirmed=model.confirmed(decision);
            for(JsonElement item:HistoryIndex.array(decision,"candidates"))if(item.isJsonObject()){
                JsonObject c=item.getAsJsonObject();boolean selected=chosen.equals(HistoryIndex.str(c,"candidate_id"));
                String type=HistoryIndex.str(c,"type");if(type.equals("leave")&&!selected)continue;
                JsonObject card=c.has("card")?HistoryIndex.obj(c,"card"):null;
                String label=card!=null?cardName(card):c.has("label")?HistoryIndex.str(c,"label"):c.has("relic")?HistoryScreen.relicName(HistoryIndex.str(c,"relic")):type.equals("skip")?tr("跳过","Skip"):type.equals("bowl")?tr("歌唱碗：生命上限 +2","Singing Bowl: max HP +2"):type.equals("leave")?tr("离开","Leave"):type.equals("shop_purge")?tr("移除卡牌","Remove card"):host.friendly(type);
                label=label.replaceAll("#[rgbypw]","").replace(" NL "," ");
                if(c.has("price"))label+=" · "+HistoryIndex.num(c,"price",0)+tr(" 金币"," gold");
                String prefix=selected?(confirmed?tr("已选  ","Picked  "):tr("未确认  ","Unconfirmed  ")):tr("备选  ","Offered  ");
                rows.add(new Entry(prefix+label,card,selected?(confirmed?Settings.GREEN_TEXT_COLOR:Settings.GOLD_COLOR):MUTED));
            }
        }
        for(JsonObject change:floor.changes){HistoryLayout.Delta delta=HistoryLayout.delta(HistoryIndex.obj(change,"before"),HistoryIndex.obj(change,"after"));if(delta.identitiesComplete)for(HistoryLayout.Change card:delta.cards){JsonObject value=card.kind.equals("removed")?card.before:card.after;rows.add(new Entry((card.kind.equals("added")?tr("获得  ","Gained  "):card.kind.equals("removed")?tr("移除  ","Removed  "):tr("变化  ","Changed  "))+cardName(value),value,card.kind.equals("removed")?Color.SALMON:Settings.GREEN_TEXT_COLOR));}}
        if(rows.isEmpty())rows.add(new Entry(tr("本层没有记录到选择或牌组变化。","No choice or deck change recorded on this floor."),null,MUTED));
        return rows;
    }
    String cardName(JsonObject card){String name=HistoryIndex.str(card,"name");if(name.isEmpty())name=HistoryScreen.cardName(HistoryIndex.str(card,"id"));int upgrades=HistoryIndex.num(card,"upgrades",0);return upgrades>0?name.replaceFirst("\\+\\d*$","")+"+"+(upgrades>1?upgrades:""):name;}
    void renderDeck(SpriteBatch sb,JsonObject state,float top){
        List<RunReviewModel.CardCount> deck=RunReviewModel.deck(state);
        for(int i=scroll*4;i<Math.min(deck.size(),(scroll+5)*4);i++){
            RunReviewModel.CardCount entry=deck.get(i);int index=i-scroll*4;float x=.06f+(index%4)*.225f,y=top-(index/4)*.029f;
            drawCard(sb,entry.card,x,y,.026f,.027f);
            text(sb,host.fit((entry.count>1?entry.count+"× ":"")+cardName(entry.card),w(.185f)),x+.032f,y,HistoryIndex.num(entry.card,"upgrades",0)>0?Settings.GREEN_TEXT_COLOR:Settings.CREAM_COLOR);
            if(InputHelper.mX>=w(x)&&InputHelper.mX<w(x+.215f)&&InputHelper.mY>h(y-.027f)&&InputHelper.mY<h(y+.006f))TipHelper.renderGenericTip(w(.57f),h(.58f),cardName(entry.card),host.card(entry.card));
        }
        if(deck.size()>20)text(sb,tr("滚轮查看牌组","Scroll to see more cards"),.71f,.333f,MUTED);
    }
    void drawCard(SpriteBatch sb,JsonObject record,float x,float top,float width,float height){AbstractCard card=CardLibrary.getCard(HistoryIndex.str(record,"id"));sb.setColor(Color.WHITE);if(card!=null&&card.portrait!=null)sb.draw(card.portrait,w(x),h(top-height),w(width),h(height));}
    void renderRelics(SpriteBatch sb,JsonObject state,float x,float y,float width){JsonArray relics=HistoryIndex.array(state,"relics");float step=Math.min(.035f,width/Math.max(1,relics.size()));for(int i=0;i<relics.size();i++){JsonObject item=relics.get(i).getAsJsonObject();AbstractRelic relic=RelicLibrary.getRelic(HistoryIndex.str(item,"id"));float xx=x+i*step,size=Math.min(w(step*.90f),h(.039f));if(relic!=null&&relic.img!=null){sb.setColor(Color.WHITE);sb.draw(relic.img,w(xx),h(y)-size/2,size,size);}if(InputHelper.mX>=w(xx)&&InputHelper.mX<w(xx+step)&&Math.abs(InputHelper.mY-h(y))<size/2)TipHelper.renderGenericTip(w(.57f),h(.57f),HistoryScreen.relicName(HistoryIndex.str(item,"id")),relic==null?"":relic.description);}}
    static Texture roomIcon(String room){switch(room){case "MonsterRoom":return ImageMaster.MAP_NODE_ENEMY;case "MonsterRoomElite":case "MonsterRoomBoss":return ImageMaster.MAP_NODE_ELITE;case "RestRoom":return ImageMaster.MAP_NODE_REST;case "ShopRoom":return ImageMaster.MAP_NODE_MERCHANT;case "TreasureRoom":case "TreasureRoomBoss":return ImageMaster.MAP_NODE_TREASURE;case "EventRoom":return ImageMaster.MAP_NODE_EVENT;default:return null;}}
    static Color roomColor(String room){return room.contains("Boss")?Color.SALMON:room.contains("Elite")?new Color(.8f,.45f,.85f,1):room.equals("RestRoom")?Color.ORANGE:room.equals("ShopRoom")?Settings.GREEN_TEXT_COLOR:room.equals("EventRoom")?GOLD:Settings.CREAM_COLOR;}
}
