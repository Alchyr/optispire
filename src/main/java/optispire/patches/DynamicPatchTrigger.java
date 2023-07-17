/*package optispire.patches;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import javassist.*;
import javassist.bytecode.BadBytecode;
import org.clapper.util.classutil.*;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;


public class DynamicPatchTrigger {
    public static void begin(CtBehavior ctBehavior) throws NotFoundException, BadBytecode {
        if (true)
            return;

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
        patches.add(new UselessInstrument());
        //patches.add(new ImageMasterTemporarify());
        //patches.add(new CommonTextureStuff());
        //patches.add(new CardImages());
        //patches.add(new TextureGenerationMapping());

        //do patches
        for (DynamicPatch patch : patches) {
            try {
                patch.prep(pool);
            } catch (NotFoundException | CannotCompileException e) {
                e.printStackTrace();
            }
        }

        Collection<?> references;
        boolean modified, alreadyModified;
        Field modifiedField = null;


        List<BiFunction<CtClass, ClassPool, Boolean>> processes = new ArrayList<>();
        int step = 0;

        while (!patches.isEmpty()) {
            processes.clear();
            int temp = step;
            patches.removeIf((p)->temp > p.maxStep);
            for (DynamicPatch patch : patches) {
                BiFunction<CtClass, ClassPool, Boolean> process = patch.getProcess(temp);
                if (process != null)
                    processes.add(process);
            }

            if (processes.isEmpty()) {
                ++step;
                continue;
            }

            classloop:
            for (ClassInfo classInfo : foundClasses) {
                try {
                    CtClass ctClass = pool.get(classInfo.getClassName());
                    references = ctClass.getRefClasses();
                    if (references == null)
                        continue classloop;

                    for (Object s : references) {
                        if (pool.getOrNull(s.toString()) == null) {
                            //refers to an unloaded class, skip
                            continue classloop;
                        }
                    }

                    modified = false;
                    alreadyModified = ctClass.isModified();

                    for (BiFunction<CtClass, ClassPool, Boolean> process : processes) {
                        if (process.apply(ctClass, pool))
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
                catch (RuntimeException e) {
                    System.out.println("\t\t- Error occurred while patching class: " + classInfo.getClassName());
                    e.printStackTrace();
                    if (e.getCause() != null)
                        e.getCause().printStackTrace();
                }
                catch (NotFoundException e) {
                    System.out.println("\t\t- Class not found: " + classInfo.getClassName());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ++step;
        }

        System.out.println("Dynamic patches complete.");

    }

    public static abstract class DynamicPatch {
        //If modifying a specific class is necessary. Occurs before processing.
        final int maxStep;
        public DynamicPatch(int maxStep) {
            this.maxStep = maxStep;
        }

        public void prep(ClassPool pool) throws NotFoundException, CannotCompileException { }

        //Return true = modified, false = not modified
        public abstract BiFunction<CtClass, ClassPool, Boolean> getProcess(int step);


        //public abstract boolean process(CtClass ctClass, ClassInfo classInfo, int step) throws CannotCompileException, BadBytecode, NotFoundException;
    }
}*/