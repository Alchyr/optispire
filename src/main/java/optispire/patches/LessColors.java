package optispire.patches;

import com.badlogic.gdx.graphics.Color;
import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

@SpirePatch(
        clz = AbstractCard.class,
        method = "createCardImage"
)
public class LessColors {
    public static final Color whitheh = Color.WHITE.cpy();

    @SpireInstrumentPatch
    public static ExprEditor colorUnmaker() {
        return new ExprEditor() {
            int amt = 35;

            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("cpy") && amt > 0) {
                    --amt;
                    m.replace("$_ = " + LessColors.class.getName() + ".whitheh;");
                }
            }
        };
    }
}
