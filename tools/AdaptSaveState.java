import javassist.*;
import javassist.bytecode.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;

/** Local adaptation of the exact user-provided 1.3 binary; no upstream sources bundled. */
public final class AdaptSaveState {
    static final String SOURCE_SHA="5aab52140fb890bc1f71cde73b67958b7f43b5c5ed5c26b71b28ba3d18c6eefa";
    static final Set<String> OBSERVERS=new HashSet<>(Arrays.asList(
        "savestate/SaveState$CountLessonLearnedHitsPatch.class",
        "savestate/SaveState$CountParasitesPatch.class"));
    public static void main(String[] args)throws Exception {
        Path source=Paths.get(args[0]), output=Paths.get(args[1]);
        if(source.toRealPath().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Output must differ from the source binary");
        StringBuilder hash=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))hash.append(String.format("%02x",b&255));
        if(!SOURCE_SHA.equals(hash.toString()))throw new IllegalArgumentException("Unreviewed SaveState binary: "+hash);
        ClassPool pool=new ClassPool(true);for(int i=2;i<args.length;i++)pool.appendClassPath(args[i]);pool.appendClassPath(source.toString());
        int removed=0, retained=0;
        try(JarFile jar=new JarFile(source.toFile());JarOutputStream out=new JarOutputStream(Files.newOutputStream(output))){
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();byte[] data;
                try(InputStream in=jar.getInputStream(entry);ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)buffer.write(bytes,0,n);data=buffer.toByteArray();
                }
                if(entry.getName().endsWith(".class")){
                    CtClass cls=pool.makeClass(new ByteArrayInputStream(data));boolean changed=false;
                    for(String tag:Arrays.asList(AnnotationsAttribute.visibleTag,AnnotationsAttribute.invisibleTag)){
                        AnnotationsAttribute annotations=(AnnotationsAttribute)cls.getClassFile().getAttribute(tag);
                        if(annotations==null)continue;
                        for(String name:Arrays.asList("com.evacipated.cardcrawl.modthespire.lib.SpirePatch","com.evacipated.cardcrawl.modthespire.lib.SpirePatches","com.evacipated.cardcrawl.modthespire.lib.SpirePatch2")){
                            if(annotations.getAnnotation(name)==null)continue;
                            if(OBSERVERS.contains(entry.getName()))retained++;
                            else {annotations.removeAnnotation(name);removed++;changed=true;}
                        }
                    }
                    if(cls.getName().equals("savestate.SaveStateMod")){
                        cls.getDeclaredConstructor(new CtClass[0]).setBody("{}");
                        for(String name:Arrays.asList("receiveRender","receiveOnBattleStart","receivePostUpdate"))cls.getDeclaredMethod(name).setBody("{}");
                        cls.getDeclaredMethod("receivePostInitialize").setBody("{ java.util.HashMap cards=com.megacrit.cardcrawl.helpers.CardLibrary.cards; savestate.StateFactories.cardIds=new String[cards.size()]; int i=0; for(java.util.Iterator it=cards.keySet().iterator();it.hasNext();){String id=(String)it.next(); savestate.StateFactories.cardIds[i]=id; savestate.StateFactories.cardIdToIndexMap.put(id,Integer.valueOf(i++));} cardNameToCardMap=new java.util.HashMap(); }");
                        cls.addField(CtField.make("public static final String STATE_NOTES_ADAPTER = \"capture-only-1\";",cls));changed=true;
                    }
                    if(changed)data=cls.toBytecode();cls.detach();
                }
                JarEntry target=new JarEntry(entry.getName());target.setTime(0);out.putNextEntry(target);out.write(data);out.closeEntry();
            }
        }
        if(retained!=2||removed!=126)throw new IllegalStateException("Unexpected patch inventory: "+retained+" / "+removed);
        System.out.println("SaveState capture-only adaptation: removed="+removed+", observer_patches="+retained);
    }
}
