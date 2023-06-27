/*package optispire.library;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import optispire.library.cardmap.CardBlankHashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class CardLibraryEfficiency {
    //The card library keeps an instance of every single card in the game at all times.
    //Unfortunately, it's iterated through far too often, so there's no point trying to optimize it.

    @SpirePatch(
            clz = CardLibrary.class,
            method = "initialize"
    )
    public static class HowAboutYouDoThisInstead
    {
        @SpirePrefixPatch
        public static void h() throws NotFoundException, CannotCompileException {
            MakeCopyChecking.test();
            CardLibrary.cards = new CardBlankHashMap();

            //Step 1 done: It launches without crashing.
            //Step 2: Make it not crash everywhere else.
            //     2.1: CardLibrary. Generate the necessary cards for viewing when choosing a tab.
        }
    }

    @SpirePatch(
            clz = CardLibrary.class,
            method = "getAllCards"
    )
    public static class H
    {
        private static boolean inMethod = false;
        private static final ArrayList<AbstractCard> emptyList = new ArrayList<>();

        @SpirePrefixPatch
        public static SpireReturn<ArrayList<AbstractCard>> noRecursionThanks() {
            if (inMethod) {
                return SpireReturn.Return(emptyList);
            }
            inMethod = true;

            ArrayList<AbstractCard> retVal = new ArrayList<>();

            for (Map.Entry<String, AbstractCard> c : CardLibrary.cards.entrySet()) {
                retVal.add(c.getValue());
            }

            inMethod = false;
            return SpireReturn.Return(retVal);
        }
    }
}
*/