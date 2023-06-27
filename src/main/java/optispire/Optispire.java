package optispire;

/*
Why does modded sts take so much more ram?
Probably just due to recompiling the game and stuff


ImageMaster initializes when game is launched.
Load less of these images, only when needed?




notes:
Packmaster without opti: 3000mb
With no card images: 2600mb
Without registering anything other than loading strings: 2150mb
No packmaster (given time to settle): 1500mb
Basicmod: also around 1500mb

memory use:
A billion color objects-
FontHelper.renderSmartText
a lot of garbage, but it's just garbage.


Use weak references for card textures?
Issue- have to make sure they are disposed before they are garbage collected
store in collection using weak/phantom references
Dispose when stale (added to reference queue)
Do not provide stale objects? Have a backup 1x1 blank texture if needed

Cards keep a reference to portrait image; when card is disposed, portrait will no longer be referenced.
Just have to keep it out of other collections.



ConstructorConstructor line 33:
Gson ends up using LinkedTreeMap for all the localization text
*/


import basemod.Pair;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.ImageMaster;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

@SpirePatch(
        clz = CardCrawlGame.class,
        method = "render"
)
public class Optispire {
    private static final float TICK = 4f;
    private static final int SET_LIMIT = 32;
    private static int nextSet = 0;
    private static float timer = TICK;
    @SpirePostfixPatch
    public static void update() {
        ManagedAsset.ManagedAssetReference o;
        while ((o = (ManagedAsset.ManagedAssetReference) referenceQueue.poll()) != null) {
            o.holder.dispose();
        }

        timer -= Gdx.graphics.getRawDeltaTime();
        if (timer <= 0) {
            timer = TICK;

            ArrayList<String> set = loadedSets.get(nextSet);
            Iterator<String> setIterator = set.iterator();
            while (setIterator.hasNext()) {
                String id = setIterator.next();
                ManagedAsset asset = loadedAssets.get(id);
                if (asset == null) {
                    loadedAssets.remove(id);
                    setIterator.remove();
                }
                else if (!asset.isFresh()) {
                    //old news
                    dispose(asset);
                    loadedAssets.remove(id);
                    setIterator.remove();
                }
                /*else {
                    asset.age();
                }*/
            }

            nextSet = (nextSet + 1) % loadedSets.size();
        }
    }

    private static final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private static final Map<String, ManagedAsset> loadedAssets = new HashMap<>();
    private static final ArrayList<ArrayList<String>> loadedSets = new ArrayList<>();
    static {
        loadedSets.add(new ArrayList<>());
    }

    private static final Map<String, String> textures = new HashMap<>(256);
    private static final Map<String, FileHandle> atlases = new HashMap<>(64);
    private static final Map<String, Pair<String, String>> regions = new HashMap<>(64);

    //Code attempts to access variables will pass use the ID, which will be used to find/load the necessary object
    public static void registerAtlas(String atlasID, FileHandle atlasFile) {
        atlases.put(atlasID, atlasFile);
    }

    public static void registerRegion(String regionID, String atlasID, String regionName) {
        regions.put(regionID, new Pair<>(atlasID, regionName));
    }

    public static void registerTexture(String textureID, String texturePath) {
        textures.put(textureID, texturePath);
    }

    public static <T> T getAsset(String id) {
        ManagedAsset asset = loadedAssets.get(id);
        if (asset != null) {
            asset.refresh();

            //If item has been disposed, will return null
            //This results in loading the item again, replacing entry in loaded assets
            //And existing entry in loadedSets will not be modified
            return asset.item();
        }
        return null;
    }

    //Load methods can be called either fresh, or with an old invalid version still within maps
    public static Texture loadTexture(String id) {
        String path = textures.get(id);
        if (path == null) {
            System.out.println("Attempted to load unknown texture " + id);
            return null;
        }
        Texture t = ImageMaster.loadImage(path);
        ManagedAsset holder = managedAssetPool.obtain();
        holder.setAsset(t, ManagedAsset.AssetType.TEXTURE);
        ManagedAsset old = loadedAssets.put(id, holder);
        //For this to be called, old item is null due to GC
        //If not null, already exists in a set.
        if (old == null) {
            //Store in a set, then return
            for (ArrayList<String> set : loadedSets) {
                if (set.size() < SET_LIMIT) {
                    set.add(id);
                    return t;
                }
            }
            ArrayList<String> newSet = new ArrayList<>();
            newSet.add(id);
            loadedSets.add(newSet);
        }
        else {
            //Properly dispose of it.
            dispose(old);
        }
        return t;
    }

    public static TextureAtlas.AtlasRegion loadRegion(String id) {
        Pair<String, String> regionInfo = regions.get(id);
        if (regionInfo == null) {
            System.out.println("Attempted to load unknown region " + id);
            return null;
        }
        ManagedAsset atlasHolder = loadedAssets.get(regionInfo.getKey());
        TextureAtlas atlas;
        if (atlasHolder == null || atlasHolder.item() == null) {
            atlas = loadAtlas(regionInfo.getKey());
            atlasHolder = loadedAssets.get(regionInfo.getKey());
        }
        else
            atlas = atlasHolder.item();

        TextureAtlas.AtlasRegion region = atlas.findRegion(regionInfo.getValue());
        if (region == null) {
            System.out.println("Region " + regionInfo.getValue() + " not found in atlas " + regionInfo.getKey());
            return null;
        }

        ManagedAsset holder = managedAssetPool.obtain();
        holder.setAsset(region, ManagedAsset.AssetType.REGION);
        holder.parent = atlasHolder;
        atlasHolder.dependent.add(holder);

        ManagedAsset old = loadedAssets.put(id, holder);
        //For this to be called, old item is null due to GC
        //If not null, already exists in a set.
        if (old == null) {
            //Store in a set, then return
            for (ArrayList<String> set : loadedSets) {
                if (set.size() < SET_LIMIT) {
                    set.add(id);
                    return region;
                }
            }
            ArrayList<String> newSet = new ArrayList<>();
            newSet.add(id);
            loadedSets.add(newSet);
        }
        else {
            //Properly dispose of it.
            dispose(old);
        }
        return region;
    }

    public static TextureAtlas loadAtlas(String id) {
        FileHandle atlasFile = atlases.get(id);
        if (atlasFile == null) {
            System.out.println("Attempted to load unknown atlas " + id);
            return null;
        }

        TextureAtlas atlas = new TextureAtlas(atlasFile);
        ManagedAsset holder = managedAssetPool.obtain();
        holder.setAsset(atlas, ManagedAsset.AssetType.ATLAS);

        ManagedAsset old = loadedAssets.put(id, holder);
        //For this to be called, old item is null due to GC
        //If not null, already exists in a set.
        if (old == null) {
            //Store in a set, then return
            for (ArrayList<String> set : loadedSets) {
                if (set.size() < SET_LIMIT) {
                    set.add(id);
                    return atlas;
                }
            }
            ArrayList<String> newSet = new ArrayList<>();
            newSet.add(id);
            loadedSets.add(newSet);
        }
        else {
            //Properly dispose of it.
            dispose(old);
        }
        return atlas;
    }

    public static Texture getTexture(Texture original, String id) {
        if (original != null)
            return original;

        original = getAsset(id);
        if (original != null)
            return original;

        return loadTexture(id);
    }
    public static TextureAtlas.AtlasRegion getRegion(TextureAtlas.AtlasRegion original, String id) {
        if (original != null)
            return original;

        original = getAsset(id);
        if (original != null)
            return original;

        return loadRegion(id);
    }
    public static TextureAtlas getAtlas(TextureAtlas original, String id) {
        if (original != null)
            return original;

        original = getAsset(id);
        if (original != null)
            return original;

        return loadAtlas(id);
    }

    private static void dispose(ManagedAsset asset) {
        asset.dispose();
        managedAssetPool.free(asset);
    }

    private static final Pool<ManagedAsset> managedAssetPool = new Pool<ManagedAsset>(128) {
        @Override
        protected ManagedAsset newObject() {
            return new ManagedAsset();
        }
    };

    private static class ManagedAsset implements Pool.Poolable {
        private boolean fresh = true;
        ManagedAsset parent = null;
        final List<ManagedAsset> dependent = new ArrayList<>();
        ManagedAssetReference asset = null;
        AssetType type = null;
        int[] disposeParams = empty;

        enum AssetType {
            TEXTURE,
            ATLAS,
            REGION
        }

        public void setAsset(Object o, AssetType type) {
            this.type = type;
            asset = new ManagedAssetReference(this, o, referenceQueue);
            switch (type) {
                case TEXTURE:
                    disposeParams = new int[]{((Texture) o).getTextureObjectHandle() };
                    break;
                case ATLAS:
                    ObjectSet<Texture> textures = ((TextureAtlas) o).getTextures();
                    disposeParams = new int[textures.size];
                    int i = 0;
                    for (Texture t : textures)
                        disposeParams[i] = t.getTextureObjectHandle();
                    break;
                case REGION:
                    break;
            }
        }

        public boolean isFresh() {
            return parent != null ? parent.isFresh() : fresh;
        }

        public void age() {
            fresh = false;
        }

        public void refresh() {
            if (parent != null)
                parent.refresh();
            fresh = asset.get() != null;
        }

        @SuppressWarnings("unchecked")
        public <T> T item() {
            return (T) asset.get();
        }

        public void dispose() {
            switch (type) {
                case TEXTURE:
                case ATLAS:
                    for (int handle : disposeParams) {
                        if (handle != 0) {
                            Gdx.gl.glDeleteTexture(handle);
                        }
                    }
                    break;
            }
            asset.clear();
            age();
        }

        @Override
        public void reset() {
            asset = null;
            type = null;
            fresh = true;
            parent = null;
            disposeParams = empty;
            dependent.clear();
        }

        private static final int[] empty = new int[] { };

        static class ManagedAssetReference extends WeakReference<Object> {
            final ManagedAsset holder;
            public ManagedAssetReference(ManagedAsset holder, Object referent, ReferenceQueue<? super Object> q) {
                super(referent, q);
                this.holder = holder;
            }
        }
    }
}

