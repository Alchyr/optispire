package optispire.patches;

import com.badlogic.gdx.graphics.Pixmap;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;

@SpirePatch(
        clz = Pixmap.class,
        method = "dispose"
)
public class PixmapLessAngry {
    @SpirePrefixPatch
    public static SpireReturn<?> noException(Pixmap __instance, boolean ___disposed) {
        if (___disposed) {
            System.out.println("Pixmap already disposed!");
            return SpireReturn.Return();
        }
        return SpireReturn.Continue();
    }
}
