package spireledger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Names for the event text the game writes into .run: the event id gets its localized name from the game itself,
 * but the recorded player_choice is the English string the event class passed in, which has no localization key.
 * A built-in table covers the vanilla choices; anything else stays as the file wrote it and is listed as missing,
 * so a player can add a line to i18n/event-choices.tsv (English TAB Chinese).
 */
final class EventText {
    static final Map<String,String> CHOICES=new LinkedHashMap<>();
    static final Set<String> MISSING=new LinkedHashSet<>(),MISSING_EVENTS=new LinkedHashSet<>();
    static {String[] pairs={
        "Ignored|忽略","Fight|战斗","Fled|逃跑","Leave|离开","AGREE|同意","Take Damage|受到伤害","Take Wound|获得伤口",
        "Card Removal|移除卡牌","Single Remove|移除一张","Purged|清除","Removed Curses|移除诅咒","Removed Attack|移除攻击牌","Removed Skill|移除技能牌","Removed Power|移除能力牌","Upgrade and Remove|升级并移除",
        "Max HP|最大生命","Lose Max HP|失去最大生命","Heal|治疗","Healed|已治疗","Full Heal|完全治疗","Damaged|受伤","Shed Blood|流血","Success|成功",
        "Memory 1|记忆 1","Memory 2|记忆 2","Memory 3|记忆 3","Read|阅读","Forget|遗忘","Change|改变","Elegance|优雅","Simplicity|简朴","Writhe|蠕动",
        "Became Test Subject|成为实验体","Became a Ghost|成为幽灵","Became a vampire|成为吸血鬼","Joined the Cult|加入邪教","Stole From Cult|偷取邪教徒的物品","Gave Idol|交出金神像",
        "Fought Bandits|与强盗战斗","Fought Mushrooms|与蘑菇战斗","Fought Nobs|与地精战斗","Fled From Nobs|逃离地精","Wore Mask|戴上面具",
        "Gather Gold|收集金币","Gave Gold|给予金币","Paid Gold|支付金币","Paid|已支付","Paid Fearfully|惊恐地支付","Left Gold|留下金币","Gold|金币",
        "Got Potions|获得药水","Gave Potion|给予药水","Bought 0 Potions|买了 0 瓶药水","Bought 1 Potion|买了 1 瓶药水","Bought 2 Potions|买了 2 瓶药水","Bought 3 Potions|买了 3 瓶药水",
        "Obtained Relic|获得遗物","Obtained Book|获得书籍","Traded Relic|交换遗物","Stole Relic|偷取遗物","Relic|遗物",
        "Offered Basic|献上基础牌","Offered Common|献上普通牌","Offered Uncommon|献上罕见牌","Offered Rare|献上稀有牌","Offered Special|献上特殊牌","Offered Curse|献上诅咒",
        "Gave Card|给予卡牌","Copied|复制","Upgraded|升级","Upgrade|升级","Upgraded Two|升级两张","Transformed|变化","Transformed Cards|变化卡牌","Forge|锻造","Pray|祈祷","Touch|触碰",
        "Entered Light|走入光明","Embrace Madness|拥抱疯狂","Desecrate|亵渎","Smashed Altar|砸碎祭坛","Bet on Murderer|押注凶手","Bet on Owner|押注主人","Inject Mutagens|注入诱变剂",
        "Donut|甜甜圈","Banana|香蕉","Cursed|被诅咒","Trade|交易","Rummage|翻找","Opened|打开","0 cards matched|匹配 0 张","1 cards matched|匹配 1 张","2 cards matched|匹配 2 张","3 cards matched|匹配 3 张",
        "Searched '0' times|搜寻 0 次","Searched '1' times|搜寻 1 次","Searched '2' times|搜寻 2 次","Searched '3' times|搜寻 3 次"};
        for(String pair:pairs){int i=pair.indexOf('|');CHOICES.put(pair.substring(0,i),pair.substring(i+1));}
        // The scrap-ooze and knowing-skull styles write their rewards as a run of tokens.
        CHOICES.put("CARD","卡牌");CHOICES.put("POTION","药水");CHOICES.put("GOLD","金币");}

    /** Where a player adds their own lines; a missing or unreadable file is not an error. */
    static Path file(){try{Path root=StateMod.ROOT.getParent();return root==null?null:root.resolve("i18n").resolve("event-choices.tsv");}catch(Throwable ignored){return null;}}
    static void loadUserFile(){
        Path path=file();if(path==null||!Files.isRegularFile(path))return;
        try{for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)){String row=line.trim();if(row.isEmpty()||row.startsWith("#"))continue;int tab=row.indexOf('\t');if(tab>0)CHOICES.put(row.substring(0,tab).trim(),row.substring(tab+1).trim());}}
        catch(Exception ignored){}
    }

    /** The display text of a recorded player_choice; an unmapped text stays as written and is remembered as missing. */
    static String display(String raw){
        if(raw==null)return "";
        String value=raw.trim().replaceAll("\\s+"," ");
        if(value.isEmpty())return "";
        String whole=CHOICES.get(value);if(whole!=null)return whole;
        // Reward tokens are space separated and may repeat ("GOLD GOLD"), so each token is translated on its own.
        String[] parts=value.split(" ");boolean tokens=false;
        for(String part:parts)if(isToken(part)){tokens=true;break;}
        if(tokens){StringBuilder out=new StringBuilder();boolean ok=true;
            for(String part:parts){String name=CHOICES.get(part);if(name==null){ok=false;break;}if(out.length()>0)out.append(' ');out.append(name);}
            if(ok)return out.toString();}
        MISSING.add(value);return value;
    }
    static boolean isToken(String part){return part.equals("CARD")||part.equals("POTION")||part.equals("GOLD");}
    static List<String> missing(){return new ArrayList<>(MISSING);}
    static void clearMissing(){MISSING.clear();}
}
