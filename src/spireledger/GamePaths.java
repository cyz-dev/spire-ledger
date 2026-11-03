package spireledger;

import com.badlogic.gdx.Gdx;
import java.nio.file.*;

/**
 * Where the game lives. Exports and the encounter-art override folder sit next to the game (the parent of mods/),
 * so a player finds them in the install folder; a read-only install falls back to the user data folder instead of failing.
 */
final class GamePaths {
    private GamePaths(){}

    /** The folder that holds mods/, or null when this is not running inside a game install (offline checks). */
    static Path game(){
        try{
            Path mods=Gdx.files.local("mods").file().toPath().toAbsolutePath().normalize();
            if(Files.isDirectory(mods))return mods.getParent();
        }catch(Throwable ignored){}
        return null;
    }

    /** The game folder when it can be written, otherwise the user data folder: a read-only install must not break export or art. */
    static Path dir(String name){
        Path game=game();
        if(game!=null){Path path=game.resolve(name);try{Files.createDirectories(path);return path;}catch(Exception ignored){}}
        Path fallback=StateMod.ROOT.getParent().resolve(name);
        try{Files.createDirectories(fallback);}catch(Exception ignored){}
        return fallback;
    }

    /** Export root: one new charts-* folder per export goes in here. */
    static Path exports(){return dir("exports");}

    /** The player's override pictures; the game's own art is drawn at runtime, so this folder is optional. */
    static Path monsterImages(){return dir("monster-images");}

    /** Where the image folder is, written without a drive letter or user name so exported metadata can be shared. */
    static String monsterImagesLabel(){
        Path dir=monsterImages(),game=game();
        if(game!=null&&dir.startsWith(game))return "<game>/"+game.relativize(dir).toString().replace('\\','/');
        return "~/.spire-ledger/monster-images";
    }
}
