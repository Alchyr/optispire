package optispire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.helpers.FontHelper;

public class AggressiveGC {
    @SpirePatch(
            cls = "basemod.BaseMod",
            method = "publishPostInitialize",
            optional = true
    )
    @SpirePatch(
            cls = "basemod.BaseMod",
            method = "publishEditCards",
            optional = true
    )
    @SpirePatch(
            cls = "basemod.BaseMod",
            method = "publishEditRelics",
            optional = true
    )
    @SpirePatch(
            cls = "basemod.BaseMod",
            method = "publishEditCharacters",
            optional = true
    )
    @SpirePatch(
            cls = "basemod.BaseMod",
            method = "publishEditStrings",
            optional = true
    )
    @SpirePatch(
            cls = "basemod.BaseMod",
            method = "publishEditKeywords",
            optional = true
    )
    @SpirePatch(
            clz = FontHelper.class,
            method = "initialize"
    )
    public static class Initialization {
        @SpirePostfixPatch
        public static void after() {
            System.gc();
        }
    }
}
