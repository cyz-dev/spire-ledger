import javassist.*;
import javassist.bytecode.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
/** Embed the audited adapter's classes only; one Mod identity and one initializer. */
public final class BundleSaveState {
 public static void main(String[] args)throws Exception{
  ClassPool pool=new ClassPool(true);pool.appendClassPath(args[0]);for(int i=2;i<args.length;i++)pool.appendClassPath(args[i]);
  Path target=Paths.get(args[1]);int count=0;
  try(JarFile jar=new JarFile(args[0])){Enumeration<JarEntry> entries=jar.entries();while(entries.hasMoreElements()){
   JarEntry entry=entries.nextElement();String name=entry.getName();if(!name.endsWith(".class")||!(name.startsWith("savestate/")||name.startsWith("ui/")))continue;
   byte[] bytes;try(InputStream in=jar.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);bytes=out.toByteArray();}
   if(name.equals("savestate/SaveStateMod.class")){CtClass c=pool.makeClass(new ByteArrayInputStream(bytes));for(String tag:Arrays.asList(AnnotationsAttribute.visibleTag,AnnotationsAttribute.invisibleTag)){AnnotationsAttribute a=(AnnotationsAttribute)c.getClassFile().getAttribute(tag);if(a!=null)a.removeAnnotation("com.evacipated.cardcrawl.modthespire.lib.SpireInitializer");}c.addField(CtField.make("public static final String STATE_NOTES_EMBEDDED = \"embedded-1\";",c));bytes=c.toBytecode();c.detach();}
   Path file=target.resolve(name);Files.createDirectories(file.getParent());Files.write(file,bytes);count++;
  }}System.out.println("Embedded audited SaveState classes: "+count+"; no upstream Mod metadata/resources/initializer");
 }
}
