package sts1state;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
/** Explicit ownership of the embedded initializer; reject duplicate savestate classes. */
final class EmbeddedSaveState {
 static boolean initialized;
 static boolean present(){try{return "embedded-1".equals(Class.forName("savestate.SaveStateMod").getField("STATE_NOTES_EMBEDDED").get(null))&&StateMod.class.getProtectionDomain().getCodeSource().getLocation().equals(Class.forName("savestate.SaveStateMod").getProtectionDomain().getCodeSource().getLocation());}catch(Exception e){return false;}}
 static void initialize(){
  if(initialized)return;
  if(Loader.MODINFOS!=null)for(ModInfo info:Loader.MODINFOS)if("SaveStateMod".equalsIgnoreCase(info.ID))throw new IllegalStateException("State Notes includes SaveState. Disable the original SaveStateMod in ModTheSpire before launching.");
  if(!present())throw new IllegalStateException("State Notes embedded SaveState is missing or shadowed; remove duplicate SaveState JARs.");
  try{Class.forName("savestate.SaveStateMod").getMethod("initialize").invoke(null);initialized=true;}catch(Exception e){throw new IllegalStateException("Embedded SaveState initialization failed",e);}
 }
}
