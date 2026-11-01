package spireledger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Skeleton;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.monsters.*;
import com.megacrit.cardcrawl.monsters.exordium.*;
import com.megacrit.cardcrawl.random.Random;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;

/** Encounter art drawn from the player's own game files: the monsters the game spawns for an encounter key, in their idle pose, cropped to what was drawn. Nothing is bundled. */
final class EncounterArt {
    static final Field ATLAS=field(AbstractCreature.class,"atlas"),SKELETON=field(AbstractCreature.class,"skeleton"),IMG=field(AbstractMonster.class,"img"),BODY=field(Hexaghost.class,"body");
    static Field field(Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(Exception e){throw new IllegalStateException(e);}}

    /** Null when the game does not know the key or draws nothing for it; the caller then keeps the blank frame. Must run on the GL thread. */
    static Texture render(SpriteBatch sb,String key){
        MonsterGroup group=spawn(key);if(group==null)return null;
        try{Pixmap black=draw(sb,group,0);try{Pixmap white=draw(sb,group,1);try{return unmix(black,white);}finally{white.dispose();}}finally{black.dispose();}}
        catch(Exception e){return null;}
        finally{dispose(group);}
    }

    /** Spawning rolls HP and, for some groups, the members on the dungeon RNGs; fresh ones stand in so browsing never advances a run's RNG. */
    static MonsterGroup spawn(String key){
        List<Field> fields=new ArrayList<>();List<Object> saved=new ArrayList<>();MonsterGroup group=null;
        try{
            for(Field f:AbstractDungeon.class.getFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Random.class){fields.add(f);saved.add(f.get(null));f.set(null,new Random(0L));}
            group=MonsterHelper.getEncounter(key);
        }catch(Throwable e){group=null;}
        finally{for(int i=0;i<fields.size();i++)try{fields.get(i).set(null,saved.get(i));}catch(Exception ignored){}}
        // getEncounter answers an unknown key with the Apology Slime.
        if(group!=null&&!"Apology Slime".equals(key))for(AbstractMonster m:group.monsters)if(m instanceof ApologySlime){dispose(group);return null;}
        return group;
    }
    static void dispose(MonsterGroup group){for(AbstractMonster m:group.monsters)try{m.dispose();}catch(Exception ignored){}}

    /** One full-screen pass on a black or white backdrop in the game's screen coordinates; the bound framebuffer, viewport and batch state are restored, so this also works inside an export band. */
    static Pixmap draw(SpriteBatch sb,MonsterGroup group,float backdrop)throws Exception{
        int w=Settings.WIDTH,h=Settings.HEIGHT;IntBuffer bound=BufferUtils.newIntBuffer(16),view=BufferUtils.newIntBuffer(16);
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,bound);Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,view);
        boolean drawing=sb.isDrawing();if(drawing)sb.end();
        Matrix4 projection=new Matrix4(sb.getProjectionMatrix()),transform=new Matrix4(sb.getTransformMatrix()),polygon=new Matrix4(CardCrawlGame.psb.getProjectionMatrix()),screen=new Matrix4().setToOrtho2D(0,0,w,h);
        FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
        try{
            buffer.bind();Gdx.gl.glViewport(0,0,w,h);Gdx.gl.glClearColor(backdrop,backdrop,backdrop,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            sb.setProjectionMatrix(screen);sb.setTransformMatrix(new Matrix4());CardCrawlGame.psb.setProjectionMatrix(screen);sb.begin();
            for(AbstractMonster m:group.monsters)draw(sb,m);
            sb.end();return ScreenUtils.getFrameBufferPixmap(0,0,w,h);
        }finally{
            if(sb.isDrawing())sb.end();Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,bound.get(0));Gdx.gl.glViewport(view.get(0),view.get(1),view.get(2),view.get(3));buffer.dispose();
            sb.setProjectionMatrix(projection);sb.setTransformMatrix(transform);CardCrawlGame.psb.setProjectionMatrix(polygon);sb.setColor(Color.WHITE);if(drawing)sb.begin();
        }
    }
    /** The body part of AbstractMonster.render, without the health bar and intent that need a room and a player. */
    static void draw(SpriteBatch sb,AbstractMonster m)throws Exception{
        // Hexaghost draws its body itself before the shared monster render; TheGuardian adds nothing, AwakenedOne only wing particles.
        if(m instanceof Hexaghost)((HexaghostBody)BODY.get(m)).render(sb);
        Skeleton skeleton=(Skeleton)SKELETON.get(m);
        if(ATLAS.get(m)==null||skeleton==null){Texture img=(Texture)IMG.get(m);if(img!=null){sb.setColor(Color.WHITE);float iw=img.getWidth()*Settings.scale,ih=img.getHeight()*Settings.scale;
            sb.draw(img,m.drawX-iw/2+m.animX,m.drawY+m.animY,iw,ih,0,0,img.getWidth(),img.getHeight(),m.flipHorizontal,m.flipVertical);}return;}
        // The game positions the skeleton after updating it, which only shows from the next frame on; a single frame needs the position first.
        skeleton.setPosition(m.drawX+m.animX,m.drawY+m.animY);m.state.update(0);m.state.apply(skeleton);skeleton.updateWorldTransform();skeleton.setColor(Color.WHITE);skeleton.setFlip(m.flipHorizontal,m.flipVertical);
        sb.end();CardCrawlGame.psb.begin();AbstractCreature.sr.draw(CardCrawlGame.psb,skeleton);CardCrawlGame.psb.end();sb.begin();sb.setBlendFunction(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** White minus black is 1 - alpha whatever blending the game used; colour comes from the black pass. Cropped to drawn pixels and flipped upright. */
    static Texture unmix(Pixmap black,Pixmap white){
        int w=black.getWidth(),h=black.getHeight(),minX=w,minY=h,maxX=-1,maxY=-1;ByteBuffer pb=black.getPixels(),pw=white.getPixels();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(alpha(pb,pw,(y*w+x)*4)>8){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(maxX<0)return null;
        int cw=maxX-minX+1,ch=maxY-minY+1;Pixmap out=new Pixmap(cw,ch,Pixmap.Format.RGBA8888);ByteBuffer po=out.getPixels();
        try{
            for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){int i=(y*w+x)*4,o=((maxY-y)*cw+x-minX)*4,a=alpha(pb,pw,i);
                for(int c=0;c<3;c++)po.put(o+c,(byte)(a==0?0:Math.min(255,(pb.get(i+c)&255)*255/a)));po.put(o+3,(byte)a);}
            Texture texture=new Texture(out);texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return texture;
        }finally{out.dispose();}
    }
    static int alpha(ByteBuffer black,ByteBuffer white,int i){int d=0;for(int c=0;c<3;c++)d+=(white.get(i+c)&255)-(black.get(i+c)&255);return Math.max(0,Math.min(255,255-d/3));}
}
