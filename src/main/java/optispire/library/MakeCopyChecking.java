package optispire.library;

import basemod.abstracts.CustomCard;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.megacrit.cardcrawl.cards.AbstractCard;
import javassist.*;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import org.clapper.util.classutil.*;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.*;

public class MakeCopyChecking {
    public static Set<String> optimizableCards = new HashSet<>();

    public static void test() throws NotFoundException, CannotCompileException {
        System.out.println("Checking which cards can be optimized:");

        //The goal is very simple: Find out which cards have a makeCopy that utilizes a no-parameter constructor and nothing else.
        //Cards with a more complex makeCopy will not be optimized in the card library.

        ClassFinder finder = new ClassFinder();
        ClassPool pool = Loader.getClassPool();

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

        // Get all non-abstract subclasses for AbstractCards
        ClassFilter filter = new AndClassFilter(
                new ClassModifiersClassFilter(Modifier.PUBLIC),
                new NotClassFilter(new InterfaceOnlyClassFilter()), //perform subclass check on as few as possible
                new NotClassFilter(new AbstractClassFilter()),
                new org.clapper.util.classutil.SubclassClassFilter(AbstractCard.class)
        );

        ArrayList<ClassInfo> foundClasses = new ArrayList<>();
        finder.findClasses(foundClasses, filter);

        //System.out.println("\t- Done Finding Classes.\n\t- Checking:");

        int[] standard = new int[] { 187, 89, 183, 176 };
                /* This is the bytecode mnemonics for a card that simply calls its constructor without anything fancy.
                new 187
                dup 89
                invokespecial 183
                areturn 176
                */

        SelfNewTest tester = new SelfNewTest();

        outer: for (ClassInfo classInfo : foundClasses)
        {
            CtClass card = pool.get(classInfo.getClassName());

            //System.out.println("\t\t- " + card.getSimpleName());
            try
            {
                CtMethod copy = card.getDeclaredMethod("makeCopy", new CtClass[] { } );

                //Check if copy method utilizes no parameter constructor and nothing else
                CodeAttribute ca = copy.getMethodInfo().getCodeAttribute();

                //System.out.println("\t\t\t- " + ca.getMaxStack() + " : " + ca.getMaxLocals());

                if (ca.getMaxStack() != 2 || ca.getMaxLocals() != 1)
                {
                    System.out.println("\t\t- Non-basic makeCopy in " + card.getSimpleName());
                }
                else
                {
                    int count = 0;
                    CodeIterator ci = ca.iterator();
                    while (ci.hasNext()) {
                        int index = ci.next();
                        int op = ci.byteAt(index);

                        if (count >= 4 || op != standard[count])
                        {
                            System.out.println("\t\t- Non-basic makeCopy in " + card.getSimpleName());
                            continue outer;
                        }

                        ++count;
                    }

                    //Last check: Make sure the new operation is called on itself, and not some other class.
                    tester.test(card, copy);

                    if (tester.pass)
                    {
                        //System.out.println("\t\t\t- Can be optimized.");
                        optimizableCards.add(card.getName());
                    }
                    else
                    {
                        System.out.println("\t\t- " + card.getSimpleName() + " generates copy of a different class");
                    }
                }
            } catch (NotFoundException e) {
                CtClass directSuper = card.getSuperclass();

                while (directSuper != null && !directSuper.getName().equals(AbstractCard.class.getName()))
                {
                    if (directSuper.getName().equals(CustomCard.class.getName()))
                    {
                        optimizableCards.add(card.getName());
                        continue outer;
                    }

                    directSuper = directSuper.getSuperclass();
                }
                System.out.println("\t\t\t- I have no idea what this card (" + card.getSimpleName() + ") is doing.");
            } catch (BadBytecode badBytecode) {
                badBytecode.printStackTrace();
            }
        }
        System.out.println("\t- Done testing.");
    }


    private static class SelfNewTest
    {
        public boolean pass;
        private final Checker tester;

        public SelfNewTest() {
            tester = new Checker(this);
        }

        public void test(CtClass c, CtMethod m) throws CannotCompileException {
            pass = true;
            tester.test(c, m);
        }

        private static class Checker extends ExprEditor
        {
            private final SelfNewTest parent;
            private CtClass target;
            boolean first = true;

            public Checker(SelfNewTest parent)
            {
                this.parent = parent;
            }

            public void test(CtClass target, CtMethod m) throws CannotCompileException {
                first = true;
                this.target = target;
                m.instrument(this);
            }

            @Override
            public void edit(NewExpr e) {
                if (first)
                {
                    first = false;

                    if (!e.getClassName().equals(target.getName()))
                    {
                        parent.pass = false;
                    }
                }
                else
                {
                    parent.pass = false;
                }
            }
        }
    }
}
