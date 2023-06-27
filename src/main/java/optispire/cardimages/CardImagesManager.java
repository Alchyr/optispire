package optispire.cardimages;

import basemod.abstracts.CustomCard;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.localization.MonsterStrings;

import java.util.Map;

public class CardImagesManager {
    //CARD IMAGES: Currently all small card images are kept loaded at all times.
    //Prevent loading of small image when a card is first instantiated.
    //When update is called, or when constructed in combat, add to loaded images?
    //At end of combat, unload all cards that were loaded during combat and are not in the "permanent" (for the run) list.

    //Basegame cards keep everything in an atlas. Don't mess with them. Do unload card background atlas?
    //Check what bard does to make sure this won't mess with atlases. Only mods that use a separate image for each card.


    //Managing the way all images are loaded would be basically impossible.
    //So, just focus on basemod, specifically loadCardImage in CustomCard.


    //Test patch: With a patch that simply doesn't load images when loadCardImage is called, saves a small amount per mod.
    //Not a ton, but it'll help a bit.
    //Will need a lot more to really optimize sts.


    @SpirePatch(
            clz = CustomCard.class,
            method = "loadCardImage"
    )
    public static class NoDont {
        @SpirePrefixPatch
        public static SpireReturn<?> nope(AbstractCard __instance, String img)
        {
            return SpireReturn.Return(null);
        }
    }


    @SpirePatch(
            clz = AbstractCard.class,
            method = "renderPortrait"
    )
    public static class LoadWhenNeeded {

    }


    //Card background images should also be unloaded. Just the default ones linked to a character color. If possible.
    //Character background (character select) images unloaded if not on the right page?
    //Act resources?
}
