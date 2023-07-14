package optispire.patches;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.evacipated.cardcrawl.modthespire.lib.ByRef;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;

public class HandleRenderingFakes {
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class, boolean.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class, boolean.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, Affine2.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    TextureRegion.class, float.class, float.class, Affine2.class
            }
    )
    public static class FakeRegion {
        private static Texture temp = null;
        @SpirePrefixPatch
        public static void loadTex(TextureRegion region) {
            /*if (region instanceof ManagedAtlas.ManagedRegion) {
                ((ManagedAtlas.ManagedRegion) region).prepTexture();
            }*/
            Texture t = region.getTexture();
            if (t.isFake) {
                temp = t;
                region.setTexture(t.getRealTexture());
            }
        }

        @SpirePostfixPatch
        public static void nullTex(TextureRegion region) {
            /*if (region instanceof ManagedAtlas.ManagedRegion) {
                ((ManagedAtlas.ManagedRegion) region).nullTexture();
            }*/
            if (temp != null) {
                region.setTexture(temp);
                temp = null;
            }
        }
    }

    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class,
                    int.class, int.class, int.class, int.class,
                    boolean.class, boolean.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class,
                    int.class, int.class, int.class, int.class,
                    boolean.class, boolean.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class,
                    int.class, int.class, int.class, int.class,
                    boolean.class, boolean.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class,
                    int.class, int.class, int.class, int.class,
                    boolean.class, boolean.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class,
                    int.class, int.class, int.class, int.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class,
                    int.class, int.class, int.class, int.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float.class, float.class, float.class, float.class
            }
    )
    @SpirePatch2(
            clz = SpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float[].class, int.class, int.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float[].class, int.class, int.class
            }
    )
    @SpirePatch2(
            clz = PolygonSpriteBatch.class,
            method = "draw",
            paramtypez = {
                    Texture.class, float[].class, int.class, int.class,
                    short[].class, int.class, int.class
            }
    )
    public static class FakeTextures {
        @SpirePrefixPatch
        public static void handleFakeTexture(@ByRef Texture[] texture) {
            texture[0] = texture[0].getRealTexture();
        }
    }
}
