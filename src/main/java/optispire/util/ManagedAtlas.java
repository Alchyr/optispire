/*package optispire.util;

import basemod.Pair;
import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import optispire.Optispire;

import java.util.*;


public class ManagedAtlas extends TextureAtlas {

    private final List<String> textureIDs = new ArrayList<>();



    public ManagedAtlas(String internalPackFile) {
        this(Gdx.files.internal(internalPackFile));
    }

    public ManagedAtlas(FileHandle atlasFile) {
        this(atlasFile, atlasFile.parent());
    }

    public ManagedAtlas(FileHandle packFile, boolean flip) {
        this(packFile, packFile.parent(), flip);
    }

    public ManagedAtlas(FileHandle packFile, FileHandle imagesDir) {
        this(packFile, imagesDir, false);
    }

    public ManagedAtlas(FileHandle packFile, FileHandle imagesDir, boolean flip) {
        this(new TextureAtlasData(packFile, imagesDir, flip));
    }

    public ManagedAtlas(TextureAtlasData data) {
        super();
        load(data);
    }


    @Override
    public Array<AtlasRegion> getRegions() {
        Array<AtlasRegion> regionArray = new Array<>(regions.size());
        for (Map.Entry<String, ManagedRegion> region : regions.entrySet()) {
            regionArray.add(region.getValue());
        }
        return regionArray;
    }

    @Override
    public AtlasRegion findRegion(String name) {
        return regions.get(name);
    }

    @Override
    public AtlasRegion findRegion(String name, int index) {
        AtlasRegion r = regions.get(name);
        if (r != null && r.index == index)
            return r;
        return null;
    }

    @Override
    public Array<AtlasRegion> findRegions(String name) {
        Array<AtlasRegion> matched = new Array<>();
        Set<String> textureIDs = new HashSet<>();

        ManagedRegion r = regions.get(name);
        if (r == null)
            return matched;
        textureIDs.add(r.id);
        matched.add(new AtlasRegion(r));

        for (int i = 0; i < collisions.size(); ++i) {
            ManagedRegion region = this.collisions.get(i);
            if (region.name.equals(name)) {
                textureIDs.add(r.id);
                matched.add(new AtlasRegion(region));
            }
        }

        for (String id : textureIDs) {
            Optispire.getTexture(null, id, true);
        }

        return matched;
    }

    //Sprite stuff- unsupported, implement if issue reported
    //createPatch


    @Override
    public ObjectSet<Texture> getTextures() {
        ObjectSet<Texture> textures = new ObjectSet<>();
        //If this method is ever called, the atlas will become *permanently* loaded, if references to the textures are maintained.

        for (String id : textureIDs) {
            textures.add(Optispire.loadTexture(id, false));
        }

        return textures;
    }

    @Override
    public void dispose() {
        //Doesn't instantly dispose, but they'll be disposed soon if not still referred to
        //(Avoids instant disposal for stuff like multiple active atlases utilizing the same texture)
        for (String id : textureIDs)
            Optispire.age(id);
    }



    private void load(TextureAtlasData data) {
        //Load textures during creation process, store width/height, then dispose them afterwards.
        //Make readers for common formats (png, jpg) that will read just the width/height without fully processing the file

        ObjectMap<TextureAtlasData.Page, Pair<String, IntSize>> pageToSize = new ObjectMap<>(8);

        for (TextureAtlasData.Page page : data.getPages()) {
            String id = page.textureFile.path();
            Optispire.registerTexture(id, ()->{
                Texture t = new Optispire.OptispireTexture(id, page.textureFile, page.format, page.useMipMaps);
                t.setFilter(page.minFilter, page.magFilter);
                t.setWrap(page.uWrap, page.vWrap);
                return t;
            });
            pageToSize.put(page, new Pair<>(id, getSize(page)));
            textureIDs.add(id);
        }

        ManagedRegion atlasRegion;
        for (TextureAtlasData.Region region : data.getRegions()) {
            int width = region.width;
            int height = region.height;
            atlasRegion = new ManagedRegion(pageToSize.get(region.page), region.left, region.top, region.rotate ? height : width, region.rotate ? width : height);
            atlasRegion.index = region.index;
            atlasRegion.name = region.name;
            atlasRegion.offsetX = region.offsetX;
            atlasRegion.offsetY = region.offsetY;
            atlasRegion.originalHeight = region.originalHeight;
            atlasRegion.originalWidth = region.originalWidth;
            atlasRegion.rotate = region.rotate;
            atlasRegion.splits = region.splits;
            atlasRegion.pads = region.pads;
            if (region.flip) {
                atlasRegion.flip(false, true);
            }

            if (this.regions.putIfAbsent(atlasRegion.name, atlasRegion) != null) {
                collisions.add(atlasRegion);
            }
        }
    }

    public static class ManagedRegion extends TextureAtlas.AtlasRegion {
        public final IntSize textureSize;
        private String id;

        public ManagedRegion(Pair<String, IntSize> texInfo, int x, int y, int width, int height) {
            super(null, x, y, width, height);
            this.id = texInfo.getKey();
            this.textureSize = texInfo.getValue();
            this.setRegion(x, y, width, height);
        }

        //Called in a patch to spritebatch's draw method.
        //POSSIBLE TODO -
        //Change to a class extending Texture like ManagedTexture that will reload itself when requested if necessary.
        //Currently if someone tracks the variable from the atlas/this region anywhere, it will remain even if disposed.
        public void prepTexture() {
            setTexture(getTexture());
        }
        public void nullTexture() {
            if (id != null)
                setTexture(null);
        }

        @Override
        public Texture getTexture() {
            return Optispire.getTexture(super.getTexture(), id, true);
        }

        //This is used in the constructor.
        @Override
        public void setRegion(int x, int y, int width, int height) {
            if (textureSize == null)
                return;

            float invTexWidth = 1.0F / textureSize.width;
            float invTexHeight = 1.0F / textureSize.height;
            //left bottom right top, scaled by inverse texture width
            this.setRegion((float)x * invTexWidth, (float)y * invTexHeight, (float)(x + width) * invTexWidth, (float)(y + height) * invTexHeight);
            ReflectionHacks.setPrivate(this, TextureRegion.class, "regionWidth", Math.abs(width));
            ReflectionHacks.setPrivate(this, TextureRegion.class, "regionHeight", Math.abs(height));
        }

        public void setRegion(float u, float v, float u2, float v2) {
            if (textureSize == null)
                return;

            int width = Math.round(Math.abs(u2 - u) * (float)textureSize.width),
            height = Math.round(Math.abs(v2 - v) * (float)textureSize.height);
            ReflectionHacks.setPrivate(this, TextureRegion.class, "regionWidth", width);
            ReflectionHacks.setPrivate(this, TextureRegion.class, "regionHeight", height);
            if (width == 1 && height == 1) {
                float adjustX = 0.25F / (float)textureSize.width;
                u += adjustX;
                u2 -= adjustX;
                float adjustY = 0.25F / (float)textureSize.height;
                v += adjustY;
                v2 -= adjustY;
            }

            ReflectionHacks.setPrivate(this, TextureRegion.class, "u", u);
            ReflectionHacks.setPrivate(this, TextureRegion.class, "v", v);
            ReflectionHacks.setPrivate(this, TextureRegion.class, "u2", u2);
            ReflectionHacks.setPrivate(this, TextureRegion.class, "v2", v2);
        }

        public void setRegion(TextureRegion region) {
            if (region instanceof ManagedRegion) {
                this.textureSize.height = ((ManagedRegion) region).textureSize.height;
                this.textureSize.width = ((ManagedRegion) region).textureSize.width;
                this.id = ((ManagedRegion) region).id;
            }
            else {
                this.setTexture(region.getTexture());
                this.textureSize.height = getTexture().getHeight();
                this.textureSize.width = getTexture().getWidth();
                this.id = null;
            }
            this.setRegion(region.getU(), region.getV(), region.getU2(), region.getV2());
        }

        public void setRegion(TextureRegion region, int x, int y, int width, int height) {
            if (region instanceof ManagedRegion) {
                this.textureSize.height = ((ManagedRegion) region).textureSize.height;
                this.textureSize.width = ((ManagedRegion) region).textureSize.width;
                this.id = ((ManagedRegion) region).id;
            }
            else {
                this.setTexture(region.getTexture());
                this.textureSize.height = getTexture().getHeight();
                this.textureSize.width = getTexture().getWidth();
                this.id = null;
            }
            this.setRegion(region.getRegionX() + x, region.getRegionY() + y, width, height);
        }

        @Override
        public TextureRegion[][] split(int tileWidth, int tileHeight) {
            //This texture will probably never be disposed.
            setTexture(Optispire.loadTexture(id, false));
            TextureRegion[][] split = super.split(tileWidth, tileHeight);
            setTexture(null);
            return split;
        }
    }
}
*/