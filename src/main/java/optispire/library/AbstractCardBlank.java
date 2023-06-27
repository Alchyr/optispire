package optispire.library;

import com.megacrit.cardcrawl.cards.AbstractCard;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class AbstractCardBlank {
    private final Constructor<? extends AbstractCard> constructor;
    private final String ID;

    public AbstractCardBlank(AbstractCard c) {
        this.ID = c.cardID;
        try {
            constructor = c.getClass().getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to auto-generate makeCopy for \"optimizable\" card: " + this.ID);
        }
    }

    public AbstractCard makeCopy() {
        try {
            return constructor.newInstance();
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to auto-generate makeCopy for \"optimizable\" card: " + this.ID);
        }
    }
}
