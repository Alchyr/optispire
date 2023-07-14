/*package optispire.localization;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.reflect.TypeToken;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.HashMap;
import java.util.Map;

@SpirePatch(
        clz = ConstructorConstructor.class,
        method = "newDefaultImplementationConstructor"
)
public class UseEfficientMap {
    @SpireInsertPatch(
            locator = Locator.class
    )
    public static SpireReturn<ObjectConstructor<?>> SwapType()
    {
        return SpireReturn.Return(UseEfficientMap::makeMap);
    }

    private static synchronized Map<?, ?> makeMap() {
        return new HashMap<>();
    }

    private static class Locator extends SpireInsertLocator {
        @Override
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher finalMatcher = new Matcher.MethodCallMatcher(TypeToken.class, "get");
            return LineFinder.findInOrder(ctMethodToPatch, finalMatcher);
        }
    }
}
*/