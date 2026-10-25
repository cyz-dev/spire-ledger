package spireledger;

import basemod.interfaces.TextReceiver;
import basemod.patches.com.megacrit.cardcrawl.helpers.input.ScrollInputProcessor.TextInput;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.google.gson.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.mainMenu.*;
import com.megacrit.cardcrawl.screens.options.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native, read-only overlay. Game update is paused; its screen and reward state stay intact. */
final class HistoryScreen implements DropdownMenuListener,ScrollBarListener,TextReceiver {
    static HistoryScreen instance;boolean historyMode;final StatsScreen stats;boolean currentRequested;final Hitbox home=new Hitbox(160*Settings.scale,36*Settings.scale);
    static boolean active(){return instance!=null;}
    static boolean zh(){return Settings.language==Settings.GameLanguage.ZHS||Settings.language==Settings.GameLanguage.ZHT;}
    static String tr(String cn,String en){return zh()?cn:en;}
    static void open(){if(active()||CardCrawlGame.cardPopup.isOpen||CardCrawlGame.relicPopup.isOpen)return;instance=new HistoryScreen();instance.refresh();consume();}
    static void consume(){InputHelper.justClickedLeft=false;InputHelper.justReleasedClickLeft=false;InputHelper.justClickedRight=false;InputHelper.pressedEscape=false;com.megacrit.cardcrawl.helpers.controller.CInputActionSet.cancel.unpress();com.megacrit.cardcrawl.helpers.controller.CInputActionSet.select.unpress();com.megacrit.cardcrawl.helpers.controller.CInputActionSet.proceed.unpress();}
    final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"spire-ledger-history-read");t.setDaemon(true);return t;});
    Future<HistoryIndex.Index> indexTask;Future<JsonObject> detailTask;
    HistoryIndex.Index index=new HistoryIndex.Index();List<HistoryIndex.Summary> filtered=new ArrayList<>();
    JsonObject live,detail;HistoryIndex.Summary selected;
    final MenuCancelButton cancel=new MenuCancelButton();
    final DropdownMenu[] filters=new DropdownMenu[5];
    final int[] selections=new int[5];
    final String[] characters={"","IRONCLAD","THE_SILENT","DEFECT","WATCHER"},outcomes={"","current","victory","death","incomplete"};
    final Hitbox keyword=new Hitbox(Settings.WIDTH*.50f,40*Settings.scale),clear=new Hitbox(135*Settings.scale,40*Settings.scale),refresh=new Hitbox(135*Settings.scale,40*Settings.scale);
    final Hitbox prev=new Hitbox(150*Settings.scale,40*Settings.scale),next=new Hitbox(150*Settings.scale,40*Settings.scale),diagnostics=new Hitbox(210*Settings.scale,40*Settings.scale);
    final Hitbox[] rows=new Hitbox[6];final ScrollBar bar;
    int offset,eventIndex,lineOffset;String query="",message="";boolean typing,raw;final boolean oldPopup=CardCrawlGame.isPopupOpen;
    enum Tone {NORMAL,MUTED,HEADER,GOOD,REMOVED,WARN,CHANGED}
    static final Color MUTED=new Color(.66f,.72f,.79f,1),CYAN=new Color(.45f,.83f,.94f,1);
    static final class Row {
        final String left,right;final Tone tone,rightTone;final boolean columns;
        Row(String left,String right,Tone tone,Tone rightTone,boolean columns){this.left=left;this.right=right;this.tone=tone;this.rightTone=rightTone;this.columns=columns;}
        @Override public String toString(){return left+" "+right;}
    }
    final Hitbox[] sections=new Hitbox[4];final int[] sectionRows=new int[4];
    final Hitbox replayButton=new Hitbox(180*Settings.scale,40*Settings.scale);
    DropdownMenu turnMenu;int turnSelection;List<JsonObject> availableTurns=new ArrayList<>();JsonObject manifest;
    List<Integer> floorOrder=new ArrayList<>();int floorIndex;boolean expandedDeck;List<JsonObject> viewRecords=new ArrayList<>();DropdownMenu branchMenu;int branchSelection;List<JsonObject> events=new ArrayList<>();List<Row> lines=new ArrayList<>();
    float rowHeight(){return Math.max(FontHelper.cardDescFont_N.getLineHeight()*1.12f*1.35f,Settings.HEIGHT*.033f);}
    int visibleRows(){return Math.max(1,(int)(Settings.HEIGHT*.495f/rowHeight()));}
    int maxLineOffset(){return Math.max(0,lines.size()-visibleRows());}
    float contentWidth(){return Settings.WIDTH*.86f;}
    float cellWidth(){return (contentWidth()-Settings.WIDTH*.025f)/2;}
    Color color(Tone t){switch(t){case HEADER:return Settings.GOLD_COLOR;case GOOD:return Settings.GREEN_TEXT_COLOR;case REMOVED:return Color.SALMON;case WARN:return Settings.GOLD_COLOR;case CHANGED:return CYAN;case MUTED:return MUTED;default:return Settings.CREAM_COLOR;}}
    float measure(BitmapFont font,String value,float scale){float x=font.getData().scaleX,y=font.getData().scaleY;try{font.getData().setScale(x*scale,y*scale);return new GlyphLayout(font,value).width;}finally{font.getData().setScale(x,y);}}
    void styledText(SpriteBatch sb,String value,float x,float y,Tone tone){BitmapFont font=tone==Tone.HEADER?FontHelper.buttonLabelFont:FontHelper.cardDescFont_N;float oldX=font.getData().scaleX,oldY=font.getData().scaleY;Color old=font.getColor().cpy();try{float scale=tone==Tone.HEADER?1:1.12f;font.getData().setScale(oldX*scale,oldY*scale);FontHelper.renderFontLeftTopAligned(sb,font,value,x,y,color(tone));}finally{font.getData().setScale(oldX,oldY);font.setColor(old);}}
    String fit(String value,float width){return HistoryLayout.fit(value,width,t->measure(FontHelper.cardDescFont_N,t,1));}
    HistoryScreen(){
        cancel.show(tr("返回","Back"));home.move(Settings.WIDTH*.86f,Settings.HEIGHT*.95f);historyMode=StateMod.ready();currentRequested=historyMode;stats=new StatsScreen(this);
        for(int i=0;i<sections.length;i++){sections[i]=new Hitbox(Settings.WIDTH*.17f,40*Settings.scale);sections[i].move(Settings.WIDTH*(.15f+i*.21f),Settings.HEIGHT*.735f);}
        filters[0]=dropdown(new String[]{tr("职业：全部","Class: all"),tr("战士","Ironclad"),tr("猎人","Silent"),tr("机器人","Defect"),tr("观者","Watcher")});
        String[] a=new String[22];a[0]=tr("进阶：全部","Ascension: all");for(int i=1;i<a.length;i++)a[i]="A"+(i-1);filters[1]=dropdown(a);
        filters[2]=dropdown(new String[]{tr("结果：全部","Result: all"),tr("进行中","In progress"),tr("胜利","Victory"),tr("死亡","Death"),tr("未完成","Incomplete")});
        filters[3]=dropdown(new String[]{tr("时间：全部","Date: all"),tr("近7天","Last 7 days"),tr("近30天","Last 30 days")});
        filters[4]=dropdown(new String[]{tr("分支：全部","Branches: all"),tr("仅SL","SL only")});
        keyword.move(Settings.WIDTH*.31f,Settings.HEIGHT*.75f);clear.move(Settings.WIDTH*.70f,Settings.HEIGHT*.75f);refresh.move(Settings.WIDTH*.85f,Settings.HEIGHT*.75f);
        for(int i=0;i<rows.length;i++){rows[i]=new Hitbox(Settings.WIDTH*.83f,Settings.HEIGHT*.063f);rows[i].move(Settings.WIDTH*.48f,Settings.HEIGHT*(.585f-i*.071f));}
        prev.move(Settings.WIDTH*.33f,Settings.HEIGHT*.13f);next.move(Settings.WIDTH*.50f,Settings.HEIGHT*.13f);diagnostics.move(Settings.WIDTH*.67f,Settings.HEIGHT*.13f);replayButton.move(Settings.WIDTH*.84f,Settings.HEIGHT*.13f);
        bar=new ScrollBar(this,Settings.WIDTH*.95f,Settings.HEIGHT*.41f,Settings.HEIGHT*.42f);
    }
    DropdownMenu dropdown(String[] options){return new DropdownMenu(this,options,FontHelper.cardDescFont_N,Settings.CREAM_COLOR,8);}
    void close(){typing=false;TextInput.stopTextReceiver(this);for(DropdownMenu d:filters)d.isOpen=false;CardCrawlGame.isPopupOpen=oldPopup;cancel.hideInstantly();io.shutdownNow();instance=null;consume();}
    void refresh(){if(indexTask!=null&&!indexTask.isDone())return;live=StateMod.recorder==null?null:StateMod.recorder.document();message=tr("正在读取本地摘要…","Reading local summaries…");indexTask=io.submit(()->HistoryIndex.scan(Arrays.asList(StateMod.ROOT,StateMod.LEGACY_ROOT),live));}
    static String cardName(String id){com.megacrit.cardcrawl.cards.AbstractCard c=CardLibrary.getCard(id);return c==null?id:c.name;}
    static String relicName(String id){com.megacrit.cardcrawl.relics.AbstractRelic r=RelicLibrary.getRelic(id);return r==null||!id.equals(r.relicId)?id:r.name;}
    static String className(String id){switch(id){case "IRONCLAD":return tr("战士","Ironclad");case "THE_SILENT":return tr("猎人","Silent");case "DEFECT":return tr("机器人","Defect");case "WATCHER":return tr("观者","Watcher");default:return id.equals("unknown")?tr("未知职业","Unknown class"):id;}}
    String outcome(String id){switch(id){case "current":return tr("进行中","In progress");case "victory":return tr("胜利","Victory");case "death":return tr("死亡","Death");default:return tr("未完成","Incomplete");}}
    void filter(){filtered=HistoryIndex.filter(index.runs,characters[selections[0]],selections[1]-1,outcomes[selections[2]],selections[3]==1?7:selections[3]==2?30:0,query,selections[4]==1,System.currentTimeMillis());offset=0;}
    DropdownMenu focusMenu;List<Integer> focusEvents=new ArrayList<>();
    @Override public void changedSelectionTo(DropdownMenu menu,int index,String option){
        if(menu==focusMenu){eventIndex=focusEvents.get(index);makeLines();return;}
        if(menu==branchMenu){branchSelection=index;prepareFloors();makeLines();return;}
        if(menu==turnMenu){turnSelection=index;return;}
        for(int i=0;i<filters.length;i++)if(menu==filters[i])selections[i]=index;filter();
    }
    void clear(){Arrays.fill(selections,0);for(DropdownMenu d:filters)d.setSelectedIndex(0);query="";filter();}
    boolean click(Hitbox h){h.update();if(h.hovered&&com.megacrit.cardcrawl.helpers.controller.CInputActionSet.select.isJustPressed()){com.megacrit.cardcrawl.helpers.controller.CInputActionSet.select.unpress();return true;}if(h.hovered&&InputHelper.justClickedLeft)h.clickStarted=true;if(h.clicked){h.clicked=false;return true;}return false;}
    int controllerFocus;
    void controller(List<Hitbox> targets){if(!Settings.isControllerMode||targets.isEmpty())return;com.megacrit.cardcrawl.helpers.controller.CInputAction up=com.megacrit.cardcrawl.helpers.controller.CInputActionSet.up,down=com.megacrit.cardcrawl.helpers.controller.CInputActionSet.down,left=com.megacrit.cardcrawl.helpers.controller.CInputActionSet.left,right=com.megacrit.cardcrawl.helpers.controller.CInputActionSet.right;if(up.isJustPressed()||left.isJustPressed()){controllerFocus--;up.unpress();left.unpress();}else if(down.isJustPressed()||right.isJustPressed()){controllerFocus++;down.unpress();right.unpress();}else return;controllerFocus=Math.floorMod(controllerFocus,targets.size());com.megacrit.cardcrawl.helpers.controller.CInputHelper.setCursor(targets.get(controllerFocus));}
    void select(HistoryIndex.Summary s){
        selected=s;detail=null;manifest=null;availableTurns.clear();turnMenu=null;turnSelection=0;events.clear();lines.clear();eventIndex=lineOffset=0;raw=false;expandedDeck=false;branchMenu=null;branchSelection=0;message=tr("正在读取此局…","Reading selected run…");
        detailTask=io.submit(()->{
            JsonObject d=s.current&&live!=null&&s.id.equals(HistoryIndex.str(HistoryIndex.obj(live,"metadata"),"run_id"))?live:HistoryIndex.readDetail(s.file);
            try{
                Path runDir=s.file!=null?s.file.getParent():StateMod.ROOT.resolve(s.id);
                Path mPath=runDir.resolve("replay/manifest.json");
                if(Files.exists(mPath))manifest=TurnFiles.read(mPath);
            }catch(Exception ex){}
            return d;
        });
    }
    void update(){

        if(indexTask!=null&&indexTask.isDone()){try{index=indexTask.get();for(HistoryIndex.Summary s:index.runs){StringBuilder names=new StringBuilder();for(String id:s.cardIds)names.append(' ').append(cardName(id));for(String id:s.relicIds)names.append(' ').append(relicName(id));s.localizedSearch=names.toString().toLowerCase(Locale.ROOT);}message="";filter();if(currentRequested){currentRequested=false;for(HistoryIndex.Summary r:index.runs)if(r.current){select(r);break;}}}catch(Exception e){message=tr("读取失败：","Read failed: ")+e.getClass().getSimpleName();}indexTask=null;}
        if(detailTask!=null&&detailTask.isDone()){try{detail=detailTask.get();for(JsonElement e:HistoryIndex.array(detail,"records"))if(e.isJsonObject()){JsonObject o=e.getAsJsonObject();String t=HistoryIndex.str(o,"type");if(Arrays.asList("decision","build_change","load","floor","ending","coverage","turn_snapshot","turn_rollback").contains(t))events.add(o);}message="";prepareFloors();makeLines();}catch(Exception e){message=tr("此局文件无法读取：","Cannot read run: ")+e.getClass().getSimpleName();}detailTask=null;}
        if(!historyMode){stats.update();return;}if(click(home)){historyMode=false;selected=null;stats.rebuild();consume();return;}
        cancel.update();if(cancel.hb.clicked||InputHelper.pressedEscape){cancel.hb.clicked=false;if(selected!=null){selected=null;detail=null;detailTask=null;lineOffset=0;consume();}else close();return;}
        if(selected==null){for(DropdownMenu d:filters)d.update();boolean dropdownOpen=false;for(DropdownMenu d:filters)dropdownOpen|=d.isOpen;if(dropdownOpen)return;List<Hitbox> controls=new ArrayList<>();for(DropdownMenu d:filters)controls.add(d.getHitbox());controls.addAll(Arrays.asList(keyword,clear,refresh));for(int i=0;i<rows.length&&offset+i<filtered.size();i++)controls.add(rows[i]);controls.addAll(Arrays.asList(prev,next,home,cancel.hb));controller(controls);
            if(click(keyword)){typing=true;TextInput.startTextReceiver(this);}else if(InputHelper.justClickedLeft&&!keyword.hovered){typing=false;TextInput.stopTextReceiver(this);}
            if(click(clear))clear();if(click(refresh))refresh();
            for(int i=0;i<rows.length;i++)if(offset+i<filtered.size()&&click(rows[i])){select(filtered.get(offset+i));return;}
            if(click(prev))offset-=rows.length;if(click(next))offset+=rows.length;
            if(InputHelper.scrolledDown)offset+=3;if(InputHelper.scrolledUp)offset-=3;
            offset=Math.max(0,Math.min(Math.max(0,filtered.size()-rows.length),offset));
        }else {
            if(focusMenu!=null){focusMenu.update();if(focusMenu.isOpen)return;}
            if(raw&&branchMenu!=null){branchMenu.update();if(branchMenu.isOpen)return;}
            if(turnMenu!=null){turnMenu.update();if(turnMenu.isOpen)return;}
            List<Hitbox> controls=new ArrayList<>(Arrays.asList(sections));
            if(focusMenu!=null)controls.add(focusMenu.getHitbox());
            if(raw&&branchMenu!=null)controls.add(branchMenu.getHitbox());
            if(turnMenu!=null)controls.add(turnMenu.getHitbox());
            controls.addAll(Arrays.asList(prev,next,diagnostics,replayButton,home,cancel.hb));
            controller(controls);
            for(int i=0;i<sections.length;i++)if(click(sections[i])){if(i==0)expandedDeck=false;else if(i==1||i==3)expandedDeck=true;makeLines();lineOffset=sectionRows[i];}
            if(click(prev)&&floorIndex>0){floorIndex--;focusFloor();makeLines();}
            if(click(next)&&floorIndex+1<floorOrder.size()){floorIndex++;focusFloor();makeLines();}
            if(click(diagnostics)){raw=!raw;makeLines();}
            if(!availableTurns.isEmpty()&&turnSelection<availableTurns.size()&&click(replayButton)){
                JsonObject targetTurn=availableTurns.get(turnSelection);
                Path runDir=selected.file!=null?selected.file.getParent():StateMod.ROOT.resolve(selected.id);
                if(ReplayLauncher.canReplayCurrent(targetTurn)){
                    try{
                        Path snapshotPath=ReplayLauncher.resolveTurnPath(runDir, Recorder.string(targetTurn, "relative_path"));
                        ReplayLauncher.replayCurrent(targetTurn,snapshotPath);
                        close();
                        return;
                    }catch(Exception ex){
                        DiagnosticReport.error("history_replay",ex);
                        message=ReplaySafety.quarantined?tr("恢复与回滚失败，游戏已冻结。请关闭游戏，重新读取原存档。","Restore and rollback failed. Gameplay is frozen. Close the game and reload the native save."):tr("当前战斗恢复失败：","In-combat restore failed: ")+ex.getMessage();
                    }
                }else{
                    try{
                        ReplayLauncher.launchPracticeInstance(selected,targetTurn,runDir);
                        message=tr("已启动独立练习实例","Launched isolated practice instance");
                    }catch(Exception ex){
                        DiagnosticReport.error("history_replay",ex);
                        message=tr("启动练习失败：","Launch practice failed: ")+ex.getMessage();
                    }
                }
            }
            if(InputHelper.scrolledDown)lineOffset+=3;if(InputHelper.scrolledUp)lineOffset-=3;lineOffset=Math.max(0,Math.min(maxLineOffset(),lineOffset));
        }
        bar.update();bar.parentScrolledToPercent(selected==null?(filtered.size()<=6?0:(float)offset/(filtered.size()-6)):(maxLineOffset()==0?0:(float)lineOffset/maxLineOffset()));
    }
    @Override public void scrolledUsingBar(float p){if(selected==null)offset=Math.round(p*Math.max(0,filtered.size()-6));else lineOffset=Math.round(p*maxLineOffset());}
    void text(SpriteBatch sb,String value,float x,float y,Color color){FontHelper.renderFontLeftTopAligned(sb,FontHelper.cardDescFont_N,value,x,y,color);}
    void button(SpriteBatch sb,Hitbox h,String label){box(sb,h,h.hovered?new Color(.22f,.27f,.32f,1):new Color(.12f,.16f,.20f,1));FontHelper.renderFontCentered(sb,FontHelper.cardDescFont_N,label,h.cX,h.cY,Settings.CREAM_COLOR);}
    void box(SpriteBatch sb,Hitbox h,Color color){sb.setColor(color);sb.draw(ImageMaster.WHITE_SQUARE_IMG,h.x,h.y,h.width,h.height);sb.setColor(Color.WHITE);}
    
    void render(SpriteBatch sb){
        if(!historyMode){stats.render(sb);return;}
        sb.setColor(new Color(.045f,.06f,.085f,1));sb.draw(ImageMaster.WHITE_SQUARE_IMG,0,0,Settings.WIDTH,Settings.HEIGHT);sb.setColor(Color.WHITE);
        FontHelper.renderFontLeftTopAligned(sb,FontHelper.buttonLabelFont,tr("尖塔账本","Spire Ledger"),Settings.WIDTH*.06f,Settings.HEIGHT*.95f,Settings.GOLD_COLOR);
        if(selected==null){
            box(sb,keyword,typing?new Color(.21f,.26f,.32f,1):new Color(.12f,.16f,.20f,1));text(sb,fit(query.isEmpty()?tr("搜索种子 / 卡牌 / 遗物（名称或ID）","Search seed / card / relic name or ID"):query+(typing?" |":""),keyword.width-20*Settings.scale),keyword.x+10*Settings.scale,keyword.cY+10*Settings.scale,Settings.CREAM_COLOR);
            button(sb,clear,tr("清空筛选","Clear"));button(sb,refresh,tr("刷新","Refresh"));
            text(sb,tr("匹配 ","Matches ")+filtered.size()+" / "+index.runs.size()+tr(" 局 · 最新在前 · 合并重复 "," runs · newest first · duplicates ")+index.duplicates+tr(" · 损坏 "," · unreadable ")+index.errors.size(),Settings.WIDTH*.06f,Settings.HEIGHT*.68f,Settings.GOLD_COLOR);
            if(!message.isEmpty())text(sb,message,Settings.WIDTH*.06f,Settings.HEIGHT*.64f,Color.SALMON);
            if(filtered.isEmpty())text(sb,index.runs.isEmpty()?tr("暂无记录。启用记录后正常打牌即可。","No records yet. Enable recording and play."):tr("没有匹配的对局。可清空筛选。","No matching runs. Clear filters to show all."),Settings.WIDTH*.06f,Settings.HEIGHT*.55f,Settings.CREAM_COLOR);
            for(int i=0;i<rows.length&&offset+i<filtered.size();i++){
                HistoryIndex.Summary r=filtered.get(offset+i);Hitbox h=rows[i];box(sb,h,h.hovered?new Color(.20f,.25f,.30f,1):new Color(.10f,.14f,.18f,1));
                String date=r.time==0?tr("日期未知","Unknown date"):new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(r.time));float x=h.x+12*Settings.scale,y=h.y+h.height-7*Settings.scale,w=h.width-24*Settings.scale;
                text(sb,fit((r.fixture?tr("[测试] ","[fixture] "):"")+date,w*.31f),x,y,MUTED);
                text(sb,fit(className(r.character)+"  A"+(r.ascension<0?"?":r.ascension),w*.22f),x+w*.33f,y,Settings.CREAM_COLOR);
                text(sb,outcome(r.outcome),x+w*.57f,y,r.outcome.equals("victory")?Settings.GREEN_TEXT_COLOR:r.outcome.equals("death")?Color.SALMON:r.outcome.equals("current")?CYAN:Settings.GOLD_COLOR);
                text(sb,fit(tr("楼层 ","Floor ")+r.floor+(r.branches.size()>1?" · SL "+(r.branches.size()-1):""),w*.22f),x+w*.78f,y,MUTED);
                y=h.y+h.height*.40f;
                text(sb,tr("拿牌 ","Pick ")+r.picks,x,y,Settings.GREEN_TEXT_COLOR);text(sb,tr("跳过 ","Skip ")+r.skips,x+w*.16f,y,Settings.GOLD_COLOR);text(sb,tr("歌唱碗 ","Bowl ")+r.bowls,x+w*.32f,y,CYAN);
                text(sb,tr("变化 ","Changes ")+r.changes,x+w*.48f,y,Settings.CREAM_COLOR);
            }
            button(sb,prev,tr("上一页","Previous"));button(sb,next,tr("下一页","Next"));
            text(sb,(filtered.isEmpty()?0:offset+1)+"–"+Math.min(filtered.size(),offset+6)+" / "+filtered.size(),Settings.WIDTH*.75f,Settings.HEIGHT*.13f,Settings.CREAM_COLOR);
            // Render dropdowns last so open rows cover, rather than overlap, the list.
            float[] x={.06f,.28f,.43f,.62f,.80f};for(int i=0;i<filters.length;i++)filters[i].render(sb,Settings.WIDTH*x[i],Settings.HEIGHT*.84f);
            if(!index.errors.isEmpty())text(sb,fit(tr("无法读取：","Unreadable: ")+String.join("; ",index.errors),Settings.WIDTH*.70f),Settings.WIDTH*.24f,Settings.HEIGHT*.05f,Color.SALMON);
        }else{
            text(sb,className(selected.character)+" A"+selected.ascension+" · "+outcome(selected.outcome)+tr(" · 种子 "," · Seed ")+selected.seed,Settings.WIDTH*.06f,Settings.HEIGHT*.83f,Settings.GOLD_COLOR);
            text(sb,tr("楼层 ","Floor ")+(floorOrder.isEmpty()?"?":floorOrder.get(floorIndex))+"",Settings.WIDTH*.06f,Settings.HEIGHT*.78f,Settings.CREAM_COLOR);
            if(!message.isEmpty())text(sb,message,Settings.WIDTH*.06f,Settings.HEIGHT*.705f,Color.SALMON);
            String[] labels={tr("本层摘要","Floor summary"),tr("选牌前状态","Before"),tr("观察变化","Changes"),tr("结算结果","Result")};for(int i=0;i<sections.length;i++)button(sb,sections[i],labels[i]);
            for(int i=0;i<visibleRows()&&lineOffset+i<lines.size();i++){
                Row row=lines.get(lineOffset+i);float y=Settings.HEIGHT*.685f-i*rowHeight(),x=Settings.WIDTH*.06f;
                if(row.tone==Tone.HEADER||row.tone==Tone.GOOD||row.tone==Tone.WARN||row.tone==Tone.REMOVED){sb.setColor(row.tone==Tone.HEADER?new Color(.17f,.21f,.27f,1):new Color(.105f,.16f,.18f,1));sb.draw(ImageMaster.WHITE_SQUARE_IMG,x-5*Settings.scale,y-rowHeight()+3*Settings.scale,contentWidth()+10*Settings.scale,rowHeight());sb.setColor(Color.WHITE);}
                styledText(sb,row.left,x,y,row.tone);if(row.columns)styledText(sb,row.right,x+cellWidth()+Settings.WIDTH*.025f,y,row.rightTone);
            }
            text(sb,tr("滚动查看 ","Scroll ")+(lines.isEmpty()?0:lineOffset+1)+"–"+Math.min(lines.size(),lineOffset+visibleRows())+" / "+lines.size(),Settings.WIDTH*.70f,Settings.HEIGHT*.05f,MUTED);
            button(sb,prev,tr("上一层","Previous floor"));button(sb,next,tr("下一层","Next floor"));button(sb,diagnostics,raw?tr("收起诊断","Hide diagnostics"):tr("原始诊断","Diagnostics"));
            if(!availableTurns.isEmpty()&&turnSelection<availableTurns.size()){
                boolean canCurrent=ReplayLauncher.canReplayCurrent(availableTurns.get(turnSelection));
                button(sb,replayButton,canCurrent?tr("当前战斗重打","Restart turn"):tr("独立练习实例","Practice turn"));
            }
        }
        if(selected!=null&&focusMenu!=null)focusMenu.render(sb,Settings.WIDTH*.06f,Settings.HEIGHT*.78f);
        if(raw&&selected!=null&&branchMenu!=null)branchMenu.render(sb,Settings.WIDTH*.64f,Settings.HEIGHT*.78f);
        if(selected!=null&&turnMenu!=null)turnMenu.render(sb,Settings.WIDTH*.45f,Settings.HEIGHT*.78f);
        button(sb,home,tr("个人统计","Personal stats"));bar.render(sb);cancel.render(sb);
    }
    void add(String value){add(value,Tone.NORMAL);}
    void add(String value,Tone tone){BitmapFont font=tone==Tone.HEADER?FontHelper.buttonLabelFont:FontHelper.cardDescFont_N;float scale=tone==Tone.HEADER?1:1.12f;for(String line:HistoryLayout.wrap(value,contentWidth(),t->measure(font,t,scale)))lines.add(new Row(line,"",tone,tone,false));}
    void pair(String left,String right,Tone l,Tone r){List<String> a=HistoryLayout.wrap(left,cellWidth(),t->measure(FontHelper.cardDescFont_N,t,1.12f)),b=HistoryLayout.wrap(right,cellWidth(),t->measure(FontHelper.cardDescFont_N,t,1.12f));for(int i=0;i<Math.max(a.size(),b.size());i++)lines.add(new Row(i<a.size()?a.get(i):"",i<b.size()?b.get(i):"",l,r,true));}
    void section(int tab,String name){sectionRows[tab]=lines.size();add(name,Tone.HEADER);}
    static String roomName(String id,boolean zh){String[][] rooms={{"MonsterRoom","普通战斗","Monster"},{"MonsterRoomElite","精英战斗","Elite"},{"MonsterRoomBoss","首领战斗","Boss"},{"EventRoom","事件","Event"},{"RestRoom","休息处","Rest site"},{"ShopRoom","商店","Shop"},{"TreasureRoom","宝箱","Treasure"},{"TreasureRoomBoss","首领宝箱","Boss treasure"},{"NeowRoom","涅奥","Neow"},{"VictoryRoom","胜利","Victory"},{"TrueVictoryRoom","心脏胜利","Heart victory"},{"EmptyRoom","空房间","Empty room"}};for(String[] r:rooms)if(r[0].equals(id))return zh?r[1]:r[2];return null;}
    String friendly(String id){String room=roomName(id,zh());if(room!=null)return room;if(id.equals("interrupted"))return tr("中断","Interrupted");if(!zh())return id;switch(id){case "decision":return "选牌记录";case "build_change":return "构筑变化";case "load":return "读档";case "floor":return "楼层快照";case "ending":return "结局";case "coverage":return "记录范围";case "human":return "原版人类路径";case "known_automation":return "已知自动化";case "unknown":return "来源未核实";case "confirmed":return "已完成";case "pending":return "等待结算";case "conflict":return "记录冲突 · 不计完成";case "COMBAT_REWARD":return "战后奖励";case "COMBAT_SELECTION":return "战斗内选牌";case "NEOW":return "涅奥";case "EVENT":return "事件";case "OTHER":return "其他来源";case "settled":return "已结算";case "no_change_or_cancelled":return "未变化或已取消";case "victory":return "胜利";case "death":return "死亡";case "local_checkpoint_found":return "已关联本地保存点";default:return id.isEmpty()?tr("未知","Unknown"):id;}}
    String value(JsonObject o,String key){String v=HistoryIndex.str(o,key);return v.isEmpty()?"?":v;}
    String resources(JsonObject s){int occupied=0;for(JsonElement e:HistoryIndex.array(s,"potions"))if(e.isJsonObject()&&!"Potion Slot".equals(HistoryIndex.str(e.getAsJsonObject(),"id")))occupied++;return tr("生命 ","HP ")+value(s,"hp")+"/"+value(s,"max_hp")+tr("   金币 ","   Gold ")+value(s,"gold")+tr("   药水 ","   Potions ")+(s.has("potions")?occupied+" / "+HistoryIndex.array(s,"potions").size():"?");}
    String card(JsonObject c){String id=HistoryIndex.str(c,"id");String name=cardName(id);if(name.equals(id)&&!HistoryIndex.str(c,"name").isEmpty())name=HistoryIndex.str(c,"name");if(name.isEmpty())name=tr("卡牌身份缺失","Missing card identity");int u=HistoryIndex.num(c,"upgrades",0);if(u>0)name=name.replaceFirst("\\+\\d*$","")+"+"+u;else if(!c.has("upgrades"))name+=tr("（升级未知）"," (upgrade unknown)");List<String> values=new ArrayList<>();if(HistoryIndex.num(c,"misc",0)!=0)values.add(tr("永久值 ","Permanent ")+value(c,"misc"));for(String k:Arrays.asList("base_damage","base_block","base_magic"))if(HistoryIndex.num(c,k,-1)>0)values.add((k.equals("base_damage")?tr("伤害 ","Damage "):k.equals("base_block")?tr("格挡 ","Block "):tr("数值 ","Value "))+value(c,k));if(c.has("base_cost")){int cost=HistoryIndex.num(c,"base_cost",-2);if(cost>=0)values.add(tr("费用 ","Cost ")+cost);else if(cost==-1)values.add(tr("X费用","X cost"));}if(c.has("bottle_flame")||c.has("bottle_lightning")||c.has("bottle_tornado"))values.add(tr("瓶装","Bottled"));return name+(values.isEmpty()?"":" · "+String.join(" · ",values));}
    void state(int tab,String label,JsonObject s){
        section(tab,label+tr(" · 牌组 "," · Deck ")+(s.has("deck")?HistoryIndex.array(s,"deck").size():"?")+tr(" 张"," cards"));
        add(resources(s));if(s.has("room"))add(tr("位置：","Location: ")+friendly(HistoryIndex.str(s,"room"))+tr(" · 幕 "," · Act ")+value(s,"act")+tr(" / 楼层 "," / Floor ")+value(s,"floor"),Tone.MUTED);
        JsonArray deck=HistoryIndex.array(s,"deck");for(int i=0;i<deck.size();i+=2){JsonObject a=deck.get(i).getAsJsonObject(),b=i+1<deck.size()?deck.get(i+1).getAsJsonObject():null;pair((i+1)+". "+card(a),b==null?"":(i+2)+". "+card(b),HistoryIndex.num(a,"upgrades",0)>0?Tone.GOOD:Tone.NORMAL,b!=null&&HistoryIndex.num(b,"upgrades",0)>0?Tone.GOOD:Tone.NORMAL);}
        add(tr("遗物 ","Relics ")+(s.has("relics")?HistoryIndex.array(s,"relics").size():"?"),Tone.HEADER);JsonArray relics=HistoryIndex.array(s,"relics");for(int i=0;i<relics.size();i+=2)pair(relic(relics.get(i).getAsJsonObject()),i+1<relics.size()?relic(relics.get(i+1).getAsJsonObject()):"",Tone.NORMAL,Tone.NORMAL);
    }
    String relic(JsonObject o){return relicName(HistoryIndex.str(o,"id"))+(HistoryIndex.num(o,"counter",-1)>=0?tr(" · 计数 "," · Count ")+value(o,"counter"):"");}
    Map<String,JsonObject> relics(JsonObject s){Map<String,JsonObject> out=new LinkedHashMap<>();for(JsonElement e:HistoryIndex.array(s,"relics"))if(e.isJsonObject())out.put(HistoryIndex.str(e.getAsJsonObject(),"id"),e.getAsJsonObject());return out;}
    String resourceDelta(JsonObject b,JsonObject a,String key){String first=value(b,key),last=value(a,key);if(first.equals(last))return first+tr("（未变化）"," (unchanged)");String amount="";if(b.has(key)&&a.has(key)){int n=HistoryIndex.num(a,key,0)-HistoryIndex.num(b,key,0);amount=" ("+(n>0?"+":"")+n+")";}return first+" → "+last+amount;}
    void changes(JsonObject before,JsonObject after){
        section(2,tr("前后锚点的观察变化","Observed changes between snapshots"));add(tr("只对照记录的前后状态，不推断变化原因。","Observed states only; no inferred cause."),Tone.MUTED);
        pair(tr("生命 ","HP ")+resourceDelta(before,after,"hp")+tr(" / 上限 "," / Max ")+resourceDelta(before,after,"max_hp"),tr("金币 ","Gold ")+resourceDelta(before,after,"gold"),Tone.NORMAL,Tone.NORMAL);
        HistoryLayout.Delta delta=HistoryLayout.delta(before,after);if(!delta.identitiesComplete)add(tr("实例身份不完整：无法逐张准确对照，完整牌组仍在前后状态中。","Incomplete instance identities: per-card changes unavailable; full decks remain visible."),Tone.WARN);
        for(HistoryLayout.Change c:delta.cards)if(c.kind.equals("added"))add(tr("＋ 新增  ","＋ Added  ")+card(c.after),Tone.GOOD);else if(c.kind.equals("removed"))add(tr("－ 移除  ","－ Removed  ")+card(c.before),Tone.REMOVED);else {int old=HistoryIndex.num(c.before,"upgrades",0),now=HistoryIndex.num(c.after,"upgrades",0);add((old!=now?tr("↑ 升级变化  ","↑ Upgrade changed  "):tr("↔ 永久值变化  ","↔ Permanent values changed  "))+cardName(HistoryIndex.str(c.after,"id")),Tone.CHANGED);pair(tr("之前：","Before: ")+card(c.before),tr("之后：","After: ")+card(c.after),Tone.MUTED,Tone.CHANGED);}
        Map<String,JsonObject> b=relics(before),a=relics(after);boolean any=false;for(String id:b.keySet())if(!a.containsKey(id)){add(tr("－ 遗物移除  ","－ Relic removed  ")+relicName(id),Tone.REMOVED);any=true;}for(String id:a.keySet())if(!b.containsKey(id)){add(tr("＋ 遗物新增  ","＋ Relic added  ")+relic(a.get(id)),Tone.GOOD);any=true;}else if(!Objects.equals(a.get(id).get("counter"),b.get(id).get("counter"))){add(tr("↔ 遗物计数  ","↔ Relic count  ")+relicName(id)+"  "+value(b.get(id),"counter")+" → "+value(a.get(id),"counter"),Tone.CHANGED);any=true;}
        if(delta.identitiesComplete&&delta.cards.isEmpty()&&!any)add(tr("这两次锚点的牌组和遗物未变化。","Deck and relics are unchanged between these snapshots."),Tone.MUTED);
    }
    void makeDetailLines(){
        lines.clear();lineOffset=0;Arrays.fill(sectionRows,0);if(events.isEmpty()){add(tr("此局没有可显示的记录。","No displayable records."));return;}JsonObject e=events.get(eventIndex);String type=HistoryIndex.str(e,"type");JsonObject before=HistoryIndex.obj(e,"before"),observed=HistoryIndex.obj(e,"state");List<String> branches=new ArrayList<>(selected.branches);
        section(0,friendly(type)+tr(" · 楼层 "," · Floor ")+HistoryIndex.num(before,"floor",HistoryIndex.num(observed,"floor",0))+tr(" · 分支 "," · Branch ")+(branches.indexOf(HistoryIndex.str(e,"branch_id"))+1));
        if(type.equals("decision")){
            String id=HistoryIndex.str(e,"decision_id"),signature="";JsonObject result=null;boolean conflict=false;
            for(JsonObject o:viewRecords){if(id.equals(HistoryIndex.str(o,"decision_id"))&&"decision".equals(HistoryIndex.str(o,"type"))&&!HistoryIndex.signature(o).equals(HistoryIndex.signature(e)))conflict=true;if(id.equals(HistoryIndex.str(o,"decision_id"))&&"result".equals(HistoryIndex.str(o,"type"))){String sig=HistoryIndex.signature(o);if(result!=null&&!signature.equals(sig))conflict=true;result=o;signature=sig;}}
            String status=conflict?"conflict":result==null?"pending":HistoryIndex.str(result,"status");boolean confirmed=status.equals("confirmed");Tone statusTone=conflict||status.equals("interrupted")?Tone.REMOVED:confirmed?Tone.GOOD:Tone.WARN;
            add(tr("状态：","Status: ")+friendly(status),statusTone);String actor=HistoryIndex.str(e,"action_source");add(friendly(HistoryIndex.str(e,"source"))+" · "+friendly(actor),actor.equals("unknown")?Tone.WARN:Tone.MUTED);
            String chosen=HistoryIndex.str(e,"candidate_id");for(JsonElement c:HistoryIndex.array(e,"candidates"))if(c.isJsonObject()){
                JsonObject o=c.getAsJsonObject();String t=HistoryIndex.str(o,"type");boolean selected=chosen.equals(HistoryIndex.str(o,"candidate_id"));String label=t.equals("card")?card(HistoryIndex.obj(o,"card")):t.equals("skip")?tr("跳过奖励","Skip reward"):t.equals("bowl")?tr("歌唱碗 · 最大生命 +2","Singing Bowl · Max HP +2"):friendly(t);
                String prefix=selected?(confirmed?t.equals("skip")?tr("【已跳过】 ","[Skipped] "):t.equals("bowl")?tr("【已用歌唱碗】 ","[Bowl used] "):tr("【已选择】 ","[Selected] "):tr("【提交项 · 未完成】 ","[Submitted · incomplete] ")):tr("备选  ","Alternative  ");add(prefix+label,selected?(confirmed?(t.equals("skip")?Tone.WARN:t.equals("bowl")?Tone.CHANGED:Tone.GOOD):statusTone):Tone.MUTED);
            }
            if(confirmed&&result!=null)changes(before,HistoryIndex.obj(result,"after"));else {section(2,tr("结果尚不能计为完成","Result is not completed"));add(conflict?tr("结果存在冲突，未选取任意一条作为真实结算。","Conflicting results; no arbitrary result is selected."):tr("没有已确认结算；前状态仍完整保留。","No confirmed settlement; the before snapshot is retained."),statusTone);}
            state(1,tr("选牌前独立快照","Independent before snapshot"),before);
            if(confirmed&&result!=null)state(3,tr("结算后的观察状态","Observed state after settlement"),HistoryIndex.obj(result,"after"));else {section(3,tr("结算结果：","Result: ")+friendly(status));add(tr("缺失或冲突的结果不会被补造。","Missing or conflicting results are not fabricated."),statusTone);}
        }else if(type.equals("build_change")){
            String status=HistoryIndex.str(e,"status");add(tr("状态：","Status: ")+friendly(status),status.equals("settled")?Tone.CHANGED:Tone.WARN);
            add(tr("观察记录：显示实际前后差异，不将未知原因归因给玩家选择。","Observed change: no inferred attribution to a player choice."),Tone.MUTED);
            changes(before,HistoryIndex.obj(e,"after"));state(1,tr("变化前的观察状态","Observed before state"),before);state(3,tr("变化后的观察状态","Observed after state"),HistoryIndex.obj(e,"after"));
        }else{
            if(type.equals("load"))add(tr("恢复父点：","Parent checkpoint: ")+friendly(HistoryIndex.str(e,"parent_status")),Tone.MUTED);if(e.has("outcome")||e.has("status"))add(friendly(HistoryIndex.str(e,e.has("outcome")?"outcome":"status")),Tone.MUTED);state(1,tr("本条观察快照","Recorded snapshot"),observed);sectionRows[2]=sectionRows[3]=sectionRows[1];
        }
        if(raw){add(tr("原始诊断（只读）","Raw diagnostics (read-only)"),Tone.HEADER);add(e.toString(),Tone.MUTED);}
    }
    static int floor(JsonObject e){return HistoryIndex.num(e,"floor",HistoryIndex.num(HistoryIndex.obj(e,"before"),"floor",HistoryIndex.num(HistoryIndex.obj(e,"state"),"floor",HistoryIndex.num(HistoryIndex.obj(e,"after"),"floor",0))));}
    void focusFloor(){expandedDeck=false;if(floorOrder.isEmpty())return;for(int i=0;i<events.size();i++)if(floor(events.get(i))==floorOrder.get(floorIndex)){eventIndex=i;break;}for(int i=0;i<events.size();i++)if(floor(events.get(i))==floorOrder.get(floorIndex)&&"decision".equals(HistoryIndex.str(events.get(i),"type"))){eventIndex=i;break;}focusOptions();}
    void focusOptions(){focusEvents.clear();List<String> labels=new ArrayList<>();int chosen=0;for(int i=0;i<events.size();i++){JsonObject e=events.get(i);if(floor(e)!=floor(events.get(eventIndex)))continue;String type=HistoryIndex.str(e,"type");if(!Arrays.asList("decision","build_change").contains(type))continue;String title=friendly(type);if(type.equals("decision"))for(JsonElement c:HistoryIndex.array(e,"candidates"))if(c.isJsonObject()&&HistoryIndex.str(c.getAsJsonObject(),"candidate_id").equals(HistoryIndex.str(e,"candidate_id")))title+= " · "+("card".equals(HistoryIndex.str(c.getAsJsonObject(),"type"))?card(HistoryIndex.obj(c.getAsJsonObject(),"card")):friendly(HistoryIndex.str(c.getAsJsonObject(),"type")));if(i==eventIndex)chosen=focusEvents.size();focusEvents.add(i);labels.add(fit(title,Settings.WIDTH*.37f));}focusMenu=focusEvents.isEmpty()?null:dropdown(labels.toArray(new String[0]));if(focusMenu!=null)focusMenu.setSelectedIndex(chosen);}
    void prepareFloors(){
        List<String> branches=new ArrayList<>(selected.branches);if(branchMenu==null){List<String> labels=new ArrayList<>();labels.add(tr("最终尝试确认路径","Confirmed final attempt"));labels.add(tr("所有原始尝试","All original attempts"));for(int i=0;i<branches.size();i++)labels.add(tr("尝试 ","Attempt ")+(i+1));branchMenu=dropdown(labels.toArray(new String[0]));}
        StatsIndex.Run path=new StatsIndex.Run();List<JsonObject> finalPath=StatsIndex.finalPath(HistoryIndex.array(detail,"records"),path);viewRecords.clear();events.clear();for(JsonElement item:HistoryIndex.array(detail,"records"))if(item.isJsonObject()){JsonObject e=item.getAsJsonObject();boolean include=branchSelection==0?finalPath.contains(e):branchSelection==1||branchSelection-2<branches.size()&&branches.get(branchSelection-2).equals(HistoryIndex.str(e,"branch_id"));if(!include)continue;viewRecords.add(e);if(Arrays.asList("decision","build_change","load","floor","ending","coverage","turn_snapshot","turn_rollback").contains(HistoryIndex.str(e,"type")))events.add(e);}
        TreeSet<Integer> floors=new TreeSet<>();for(JsonObject e:events)floors.add(floor(e));floorOrder=new ArrayList<>(floors);floorIndex=selected.current?Math.max(0,floorOrder.size()-1):0;focusFloor();
    }
    void makeLines(){if(events.isEmpty()){lines.clear();add(tr("该尝试没有可查看的楼层记录。","No floor records in this attempt."));return;}int selectedFloor=floor(events.get(eventIndex));floorIndex=Math.max(0,floorOrder.indexOf(selectedFloor));if(expandedDeck){makeDetailLines();return;}lines.clear();lineOffset=0;Arrays.fill(sectionRows,0);section(0,tr("第 ","Floor ")+selectedFloor+tr(" 层 · 选择与构筑"," · Choices and build"));
        JsonObject context=HistoryIndex.obj(events.get(eventIndex),"before");if(context.entrySet().size()==0)context=HistoryIndex.obj(events.get(eventIndex),"state");if(context.entrySet().size()>0){add(resources(context));if(context.has("room"))add(friendly(HistoryIndex.str(context,"room")),Tone.MUTED);}
        boolean choices=false;for(JsonObject e:events)if(floor(e)==selectedFloor&&"decision".equals(HistoryIndex.str(e,"type"))){choices=true;String id=HistoryIndex.str(e,"decision_id");JsonObject result=null;boolean conflict=false;String signature="";for(JsonObject r:viewRecords)if(id.equals(HistoryIndex.str(r,"decision_id"))){String type=HistoryIndex.str(r,"type");if(type.equals("decision")&&!HistoryIndex.signature(r).equals(HistoryIndex.signature(e)))conflict=true;if(type.equals("result")){String sig=HistoryIndex.signature(r);if(result!=null&&!signature.equals(sig))conflict=true;result=r;signature=sig;}}
            boolean confirmed=!conflict&&result!=null&&"confirmed".equals(HistoryIndex.str(result,"status"));add(tr("选牌 · ","Choice · ")+friendly(HistoryIndex.str(e,"source")),Tone.HEADER);if(!confirmed)add(conflict?tr("记录冲突，结果不计完成。","Conflicting records; result not completed."):tr("该选择没有已确认结算。","No confirmed settlement for this choice."),conflict?Tone.REMOVED:Tone.WARN);
            for(JsonElement item:HistoryIndex.array(e,"candidates"))if(item.isJsonObject()){JsonObject c=item.getAsJsonObject();String type=HistoryIndex.str(c,"type");boolean chosen=HistoryIndex.str(c,"candidate_id").equals(HistoryIndex.str(e,"candidate_id"));String name=type.equals("card")?card(HistoryIndex.obj(c,"card")):type.equals("skip")?tr("跳过奖励","Skip reward"):type.equals("bowl")?tr("歌唱碗 · 最大生命 +2","Singing Bowl · Max HP +2"):friendly(type);String prefix=!chosen?tr("备选  ","Alternative  "):!confirmed?tr("【提交项 · 未完成】 ","[Submitted · incomplete] "):type.equals("skip")?tr("【已跳过】 ","[Skipped] "):type.equals("bowl")?tr("【已用歌唱碗】 ","[Bowl used] "):tr("【已选择】 ","[Selected] ");add(prefix+name,chosen?(confirmed?(type.equals("bowl")?Tone.CHANGED:type.equals("skip")?Tone.WARN:Tone.GOOD):Tone.WARN):Tone.MUTED);}
            if(confirmed)compactChanges(HistoryIndex.obj(e,"before"),HistoryIndex.obj(result,"after"));
        }
        if(!choices)add(tr("本层没有选牌记录。","No card choices recorded on this floor."),Tone.MUTED);sectionRows[2]=lines.size();for(JsonObject e:events)if(floor(e)==selectedFloor&&"build_change".equals(HistoryIndex.str(e,"type"))&&"settled".equals(HistoryIndex.str(e,"status")))compactChanges(HistoryIndex.obj(e,"before"),HistoryIndex.obj(e,"after"));
        for(JsonObject e:events)if(floor(e)==selectedFloor&&"ending".equals(HistoryIndex.str(e,"type")))add(tr("本局结局：","Result: ")+friendly(HistoryIndex.str(e,"outcome")),Tone.HEADER);
        sectionRows[1]=sectionRows[3]=0;add(tr("完整当时牌组：点击“选牌前状态”或“结算结果”。","Full observed decks: choose Before or Result."),Tone.MUTED);
        availableTurns.clear();
        boolean restoreAllowed = manifest != null && manifest.has("restore_available") && manifest.get("restore_available").getAsBoolean() && TurnAdapter.ID.equals(Recorder.string(manifest,"adapter"));
        if(restoreAllowed && manifest.has("snapshots")){
            List<String> branches = selected != null ? new ArrayList<>(selected.branches) : Collections.emptyList();
            String currentBranch = branchSelection == 0 ? (selected != null ? selected.lastBranch : "") : branchSelection == 1 ? "" : (branchSelection - 2 < branches.size() ? branches.get(branchSelection - 2) : "");
            Set<String> pathSnapshots=new HashSet<>();
            for(JsonObject event:viewRecords)if("turn_snapshot".equals(Recorder.string(event,"type")))pathSnapshots.add(Recorder.string(event,"snapshot_id"));
            for(JsonElement item:manifest.getAsJsonArray("snapshots")){
                JsonObject pt=item.getAsJsonObject();
                boolean branchMatch = branchSelection==0?pathSnapshots.contains(Recorder.string(pt,"snapshot_id")):currentBranch.isEmpty() || currentBranch.equals(Recorder.string(pt, "branch_id"));
                if(branchMatch && HistoryIndex.num(pt,"floor",0)==selectedFloor&&"recorded".equals(Recorder.string(pt,"availability"))){
                    availableTurns.add(pt);
                }
            }
        }
        if(!availableTurns.isEmpty()){
            List<String> turnLabels=new ArrayList<>();
            for(JsonObject pt:availableTurns)turnLabels.add(tr("第 ","Turn ")+HistoryIndex.num(pt,"turn",1)+tr(" 回合",""));
            turnMenu=dropdown(turnLabels.toArray(new String[0]));
            if(turnSelection>=availableTurns.size())turnSelection=0;
            turnMenu.setSelectedIndex(turnSelection);
            boolean canCurrent=ReplayLauncher.canReplayCurrent(availableTurns.get(turnSelection));
            add(tr("回合重打：已就绪（","Turn practice: ready (")+availableTurns.size()+tr(" 个回合点）· "," snapshots) · ")+(canCurrent?tr("当前战斗可直接重打","in-combat restart"):tr("退出局可启动独立练习","launch isolated practice")),Tone.GOOD);
        }else if(manifest!=null){
            if(!restoreAllowed){
                add(tr("回合重打：历史记录环境不受支持，无可恢复附件。","Turn practice: unsupported recording environment; restore unavailable."),Tone.MUTED);
            }else{
                add(tr("回合重打：本层无可恢复的回合快照。","Turn practice: no recoverable turn snapshot on this floor."),Tone.MUTED);
            }
            turnMenu=null;
        }else{
            add(tr("回合重打：旧无快照记录，无可恢复附件。","Turn practice: older run without snapshot; no recoverable attachment."),Tone.MUTED);
            turnMenu=null;
        }
        if(raw){add(tr("高级诊断 · 原始事件","Advanced diagnostics · original events"),Tone.HEADER);for(JsonObject e:events)if(floor(e)==selectedFloor)add(e.toString(),Tone.MUTED);}
    }
    void compactChanges(JsonObject before,JsonObject after){HistoryLayout.Delta delta=HistoryLayout.delta(before,after);if(!delta.identitiesComplete){add(tr("逐张变化身份未记录；完整前后牌组仍可查看。","Per-card identity unavailable; full observed decks remain available."),Tone.MUTED);return;}for(HistoryLayout.Change c:delta.cards)if(c.kind.equals("added"))add(tr("＋ 新增  ","＋ Added  ")+card(c.after),Tone.GOOD);else if(c.kind.equals("removed"))add(tr("－ 移除  ","－ Removed  ")+card(c.before),Tone.REMOVED);else add(tr("↔ 观察变化  ","↔ Observed change  ")+card(c.before)+" → "+card(c.after),Tone.CHANGED);for(String key:Arrays.asList("hp","max_hp","gold"))if(before.has(key)&&after.has(key)&&!Objects.equals(before.get(key),after.get(key)))add((key.equals("gold")?tr("金币 ","Gold "):key.equals("hp")?tr("生命 ","HP "):tr("生命上限 ","Max HP "))+resourceDelta(before,after,key),Tone.CHANGED);}
    @Override public String getCurrentText(){return query;}
    @Override public void setText(String text){query=text.length()>80?text.substring(0,80):text;filter();}
    @Override public boolean isDone(){return !typing||!active();}
    @Override public boolean acceptCharacter(char c){return !Character.isISOControl(c);}
    @Override public int getCharLimit(){return 80;}
    @Override public boolean onPushEnter(){typing=false;TextInput.stopTextReceiver(this);return true;}
}
