package optispire.patches;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import javassist.*;
import javassist.bytecode.BadBytecode;
import optispire.patches.images.ImageMasterTemporarify;
import org.clapper.util.classutil.*;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SpirePatch(
        clz = CardCrawlGame.class,
        method = SpirePatch.CONSTRUCTOR
)
public class DynamicPatchTrigger {
    public static void Raw(CtBehavior ctBehavior) throws NotFoundException {
        System.out.println("Starting dynamic patches.");

        ClassFinder finder = new ClassFinder();

        finder.add(new File(Loader.STS_JAR));

        for (ModInfo modInfo : Loader.MODINFOS) {
            if (modInfo.jarURL != null) {
                try {
                    finder.add(new File(modInfo.jarURL.toURI()));
                } catch (URISyntaxException e) {
                    // do nothing
                }
            }
        }

        ClassPool pool = ctBehavior.getDeclaringClass().getClassPool();

        // Get ALL classes.
        ClassFilter filter = new AndClassFilter(
                new NotClassFilter(new InterfaceOnlyClassFilter()),
                new GamePackageFilter() //avoids about 4000 classes
        );

        ArrayList<ClassInfo> foundClasses = new ArrayList<>();
        finder.findClasses(foundClasses, filter);

        //prep patches
        List<DynamicPatch> patches = new ArrayList<>();
        patches.add(new ImageMasterTemporarify());

        //do patches
        for (DynamicPatch patch : patches) {
            try {
                patch.directPatch(pool);
            } catch (NotFoundException | CannotCompileException e) {
                e.printStackTrace();
            }
        }

        Collection<?> references;
        boolean modified, alreadyModified;
        Field modifiedField = null;

        outer:
        for (ClassInfo classInfo : foundClasses) {
            try {
                CtClass ctClass = pool.get(classInfo.getClassName());

                references = ctClass.getRefClasses();

                if (references == null)
                    continue outer;

                for (Object s : references) {
                    if (pool.getOrNull(s.toString()) == null) {
                        //refers to an unloaded class, skip
                        continue outer;
                    }
                }

                modified = false;
                alreadyModified = ctClass.isModified();

                for (DynamicPatch patch : patches) {
                    if (patch.process(ctClass, classInfo))
                        modified = true;
                }
                if (!modified && !alreadyModified) {
                    try {
                        if (modifiedField == null) {
                            modifiedField = ctClass.getClass().getDeclaredField("wasChanged");
                            modifiedField.setAccessible(true);
                        }
                        modifiedField.set(ctClass, false);
                        //System.out.println("\t\t- Marked class as unchanged: " + ctClass.getSimpleName());
                    }
                    catch (NoSuchFieldException | IllegalAccessException e) {
                        System.out.println("\t\t- Failed to mark class as unchanged: " + ctClass.getSimpleName());
                    }
                }
            }
            catch(CannotCompileException e) {
                System.out.println("\t\t- Error occurred while patching class: " + classInfo.getClassName());
                e.printStackTrace();
            }
            catch(BadBytecode e) {
                System.out.println("\t\t- Class's canUse method has bad bytecode: " + classInfo.getClassName());
                e.printStackTrace();
            }
            catch (NotFoundException e) {
                System.out.println("\t\t- Class not found: " + classInfo.getClassName());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("Dynamic patches complete.");
    }


    public static abstract class DynamicPatch {
        //If modifying a specific class is necessary. Occurs before processing.
        public void directPatch(ClassPool pool) throws NotFoundException, CannotCompileException { }

        //Return true = modified, false = not modified
        public abstract boolean process(CtClass ctClass, ClassInfo classInfo) throws CannotCompileException, BadBytecode, NotFoundException;
    }
}