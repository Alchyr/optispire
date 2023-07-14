package optispire;

/*
Why does modded sts take so much more ram?
Probably just due to recompiling the game and stuff


CURRENT METHOD UTILIZING MANY DYNAMIC PATCHES:
unnacceptable.
takes too much ram just through the patching process.
need to handle things more like ManagedAtlas, changing behavior in just a few places.



notes:
MTS no mods: 550-600mb
MTS+ ANY MOD: 750-800mb
MTS basemod stslib memlogger: 1460 mb
1160, 1400mb allocated (increased after garbage collection)
MTS basemod stslib memlogger optispire: 1500 mb
A larger amount of memory was allocated for the heap, though.
+optimize the spire: 1600 mb
note: basicmod adds effectively no usage. What causes more ram usage?

mts is just not very efficient...



SPRITER ANIMATIONS??!?!?!?
I think every instance loads a LOT of textures. And there's multiple instances made of pretty much every creature and player and stuff that uses them.
process:
load images as pixmaps
create textures from pixmaps, texture -> textureregion -> sprite
store sprites in resources map
if pack (default true)
    combine into larger pixmaps

After loading all:
If pack: generate a TextureAtlas from the combined pixmap
    Dispose all existing sprites
    Set resource entries to entries of the atlas

dispose loaded pixmaps after

Then, create textures from pixmaps, convert to textureregions
later, convert to textureregions




Packmaster without opti: 3000mb
With no card images: 2600mb
Without registering anything other than loading strings: 2150mb
No packmaster (given time to settle): 1500mb
Basicmod: also around 1500mb



chunky List: 10000mb




ConstructorConstructor line 33:
Gson ends up using LinkedTreeMap for all the localization text
*/


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Supplier;

@SpirePatch(
        clz = CardCrawlGame.class,
        method = "render"
)
public class RamSaver {
    private static final float TICK = 5f;
    private static final int SET_LIMIT = 48;
    private static int nextSet = 0;
    private static float timer = TICK;

    /*
        ManagedAsset pooling rules:
        When an asset is asked for, get existing one if it exists, otherwise load new one.
        If existing one is disposed (weak reference), replace it with a new reference.
        The old one is disposed during in the loading process.

        After an asset is disposed, it should not exist ANYWHERE other than possibly the referenceQueue.

        On update:
        Any assets that were garbage collected will be within the reference queue.
        If they have not already been disposed, dispose them.

            On tick:
            Process 1 "set" of assets.
            Any assets that can age (disposed of if not requested) will age.
            Any old/dead assets will be cleaned up.
     */

    /*
        Atlases:

        The original texture atlas initialization is replaced
        Custom class that extends TextureAtlas.
        The atlas itself is not tracked in Optispire.
        The methods of ManagedAtlas just refer back to Optispire.
        It also returns a ManagedRegion, which refers back to Optispire in the same way.
        If the texture used by a ManagedRegion has been disposed, it will reload it.

            - When a region is requested, load page for that region (if not loaded) and add to managed assets
            - When a page is disposed, clean up all those regions (as expected)
            - The atlas and regions themselves are never put in the ManagedAsset list, remaining a permanent object containing primarily information.
     */




    @SpirePostfixPatch
    public static void update() {
        ManagedAsset.ManagedAssetReference o;
        while ((o = (ManagedAsset.ManagedAssetReference) referenceQueue.poll()) != null) {
            if (o.holder.asset == o) { //Maybe disposed and replaced while in queue
                dispose(o.holder);
            }
        }

        timer -= Gdx.graphics.getRawDeltaTime();
        if (timer <= 0) {
            timer = TICK / loadedSets.size();

            ArrayList<String> set = loadedSets.get(nextSet);
            for (int i = set.size() - 1; i >= 0; --i) {
                String id = set.get(i);
                ManagedAsset asset = loadedAssets.get(id);
                if (asset == null) {
                    loadedAssets.remove(id);
                    set.remove(i);
                }
                else if (!asset.isFresh()) {
                    //old news
                    dispose(asset);
                }
                else if (asset.canAge()) {
                    asset.age();
                }
            }

            nextSet = (nextSet + 1) % loadedSets.size();

            /*if (nextSet == 0) {
                SystemStats.logMemoryStats();
            }*/
        }
    }

    private static final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private static final Map<String, ManagedAsset> loadedAssets = new HashMap<>();
    private static final Set<String> nullAssets = new HashSet<>();
    private static final ArrayList<ArrayList<String>> loadedSets = new ArrayList<>();
    static {
        loadedSets.add(new ArrayList<>());
    }

    private static final Map<String, Supplier<Texture>> textures = new HashMap<>(256);
    /*private static final Map<String, ManagedAtlas> atlases = new HashMap<>(64);
    private static final Map<String, Supplier<TextureAtlas.AtlasRegion>> regions = new HashMap<>(64);*/

    //Code attempts to access variables will pass use the ID, which will be used to find/load the necessary object
    /*public static void registerAtlas(String atlasID, FileHandle atlasFile) {
        ManagedAtlas atlas = new ManagedAtlas(atlasFile);
        atlases.put(atlasID, atlas);
    }

    public static void registerRegion(String regionID, Supplier<TextureAtlas.AtlasRegion> regionSupplier) {
        regions.put(regionID, regionSupplier);
    }*/

    /*public static void registerTextureForRegion(String textureID, String texturePath) {
        registerTexture(textureID + "RGN", ()->Optispire.makeTexture(texturePath));
    }*/
    public static boolean textureExists(String ID) {
        return textures.containsKey(ID);
    }
    public static void registerTexture(String textureID, String texturePath) {
        registerTexture(textureID, ()-> RamSaver.makeTexture(texturePath));
    }
    public static void registerTexture(String textureID, Supplier<Texture> texSupplier) {
        textures.put(textureID, texSupplier);
    }

    //TODO - Make this return a class that overrides Texture, use it to track preferences for textures based on ID
    // (Specifically stuff like TextureFilter settings)
    private static Texture makeTexture(String path) {
        try {
            Texture t = new Texture(path);
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return t;
        }
        catch (Exception ignored) {
            System.out.println("Failed to load texture " + path);
        }
        return null;
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
    public static ManagedAsset getAssetHolder(String id) {
        ManagedAsset asset = loadedAssets.get(id);
        if (asset != null) {
            asset.refresh();
        }
        return asset;
    }

    //Load methods can be called either fresh, or with an old invalid version still within maps
    public static Texture loadTexture(String id, boolean canAge) {
        if (id == null)
            return null;
        Supplier<Texture> supplier = textures.get(id);
        if (supplier == null) {
            System.out.println("Attempted to load unknown texture " + id);
            return null;
        }
        Texture t = supplier.get();
        if (t == null) { //nulls get saved permanently. Usually means invalid filepath.
            ManagedAsset holder = managedAssetPool.obtain();
            holder.canAge = false;
            holder.setAsset(id, null, ManagedAsset.AssetType.REGION);
            loadedAssets.put(id, holder);
            nullAssets.add(id);
            return null;
        }
        ManagedAsset holder = managedAssetPool.obtain();
        holder.setAsset(id, t, ManagedAsset.AssetType.TEXTURE);
        holder.canAge = canAge;
        ManagedAsset old = loadedAssets.put(id, holder);
        //For this to be called, old item is null due to GC, not yet disposed
        //If not null, already exists in a set.
        if (old == null) {
            //Store in a set, then return
            for (ArrayList<String> set : loadedSets) {
                if (set.size() < SET_LIMIT) {
                    set.add(id);
                    holder.set = set;
                    return t;
                }
            }
            ArrayList<String> newSet = new ArrayList<>(SET_LIMIT);
            newSet.add(id);
            holder.set = newSet;
            loadedSets.add(newSet);
        }
        else {
            //Properly dispose of it.
            holder.set = old.set;
            dispose(old, false);
        }
        return t;
    }

    /*public static TextureAtlas.AtlasRegion loadRegion(String id, boolean canAge) {
        Supplier<TextureAtlas.AtlasRegion> regionSupplier = regions.get(id);
        if (regionSupplier == null) {
            System.out.println("Attempted to load unknown region " + id);
            return null;
        }

        ManagedAsset atlasHolder = getAssetHolder(regionInfo.getKey());
        ManagedAtlas atlas;
        if (atlasHolder == null || atlasHolder.item() == null) {
            atlas = loadAtlas(regionInfo.getKey(), canAge);
            if (atlas == null) {
                return null;
            }
            atlasHolder = loadedAssets.get(regionInfo.getKey());
        }
        else
            atlas = atlasHolder.item();

        TextureAtlas.AtlasRegion region = atlas.findRegion(regionInfo.getValue());
        if (region == null) {
            System.out.println("Region " + regionInfo.getValue() + " not found in atlas " + regionInfo.getKey());
            return null;
        }

        return loadRegion(id, region, atlasHolder, canAge);
    }*/
    public static TextureAtlas.AtlasRegion loadRegion(String id, TextureAtlas.AtlasRegion region, ManagedAsset parent, boolean canAge) {
        ManagedAsset holder = managedAssetPool.obtain();
        holder.setAsset(id, region, ManagedAsset.AssetType.REGION);
        holder.canAge = canAge;
        holder.parent = parent;
        parent.dependent.add(holder);

        ManagedAsset old = loadedAssets.put(id, holder);
        //For this to be called, old item is null due to GC
        //If not null, already exists in a set.
        if (old == null) {
            //Store in a set, then return
            for (ArrayList<String> set : loadedSets) {
                if (set.size() < SET_LIMIT) {
                    set.add(id);
                    holder.set = set;
                    return region;
                }
            }
            ArrayList<String> newSet = new ArrayList<>(SET_LIMIT);
            newSet.add(id);
            holder.set = newSet;
            loadedSets.add(newSet);
        }
        else {
            //Properly dispose of it.
            holder.set = old.set;
            dispose(old, false);
        }
        return region;
    }

    /*public static ManagedAtlas loadAtlas(String id, boolean canAge) {
        ManagedAtlas atlas = atlases.get(id);
        if (atlas == null) {
            System.out.println("Attempted to load unknown atlas " + id);
            return null;
        }

        ManagedAsset holder = managedAssetPool.obtain();
        holder.setAsset(id, atlas, ManagedAsset.AssetType.ATLAS);
        holder.canAge = canAge;

        ManagedAsset old = loadedAssets.put(id, holder);
        //For this to be called, old item is null due to GC
        //If not null, already exists in a set.
        if (old == null) {
            //Store in a set, then return
            for (ArrayList<String> set : loadedSets) {
                if (set.size() < SET_LIMIT) {
                    set.add(id);
                    holder.set = set;
                    return atlas;
                }
            }
            ArrayList<String> newSet = new ArrayList<>(SET_LIMIT);
            newSet.add(id);
            holder.set = newSet;
            loadedSets.add(newSet);
        }
        else {
            //Properly dispose of it.
            holder.set = old.set;
            dispose(old, false);
        }
        return atlas;
    }*/

    public static Texture getExistingTexture(String id) {
        Texture t = getAsset(id);
        return t != null && t.getTextureObjectHandle() != 0 ? t : null;
    }
    public static Texture getTexture(Texture original, String id) {
        return getTexture(original, id, true);
    }
    public static Texture getTexture(Texture original, String id, boolean canAge) {
        Texture t = getAsset(id); //get first, if t matches original will cause a refresh

        if (original != null)
            return original;

        if (nullAssets.contains(id) || (t != null && t.getTextureObjectHandle() != 0))
            return t;

        return loadTexture(id, canAge);
    }
    public static TextureAtlas.AtlasRegion getTextureAsRegion(TextureAtlas.AtlasRegion original, String id) {
        return getTextureAsRegion(original, id, false);
    }
    public static TextureAtlas.AtlasRegion getTextureAsRegion(TextureAtlas.AtlasRegion original, String id, boolean canAge) {
        //See if already loaded
        String regionID = id + "RGN";
        TextureAtlas.AtlasRegion region = getAsset(regionID);

        if (original != null)
            return original;

        if (region != null) {
            return region;
        }

        //Get/load texture
        Texture t = getTexture(null, id, canAge);
        if (t == null)
            return null;

        //Make atlasregion
        ManagedAsset texture = loadedAssets.get(id);
        region = new TextureAtlas.AtlasRegion(t, 0, 0, t.getWidth(), t.getHeight());

        return loadRegion(regionID, region, texture, false); //the region itself doesn't need to age
    }

    /*public static TextureAtlas.AtlasRegion getRegion(TextureAtlas.AtlasRegion original, String id) {
        return getRegion(original, id, false);
    }
    public static TextureAtlas.AtlasRegion getRegion(TextureAtlas.AtlasRegion original, String id, boolean canAge) {
        if (original != null)
            return original;

        original = getAsset(id);
        if (original != null)
            return original;

        return loadRegion(id, canAge);
    }

    public static TextureAtlas getAtlas(TextureAtlas original, String id) {
        return getAtlas(original, id, false);
    }
    public static TextureAtlas getAtlas(TextureAtlas original, String id, boolean canAge) {
        if (original != null)
            return original;

        original = getAsset(id);
        if (original != null)
            return original;

        return loadAtlas(id, canAge);
    }*/

    public static void age(String id) {
        ManagedAsset asset = loadedAssets.get(id);
        if (asset != null) {
            asset.age();
        }
    }
    public static void dispose(String id) {
        ManagedAsset asset = loadedAssets.get(id);
        if (asset != null) {
            dispose(asset, true);
        }
    }
    private static void dispose(ManagedAsset asset) {
        dispose(asset, true);
    }
    private static void dispose(ManagedAsset asset, boolean removeKey) {
        asset.dispose();
        if (removeKey) {
            loadedAssets.remove(asset.ID);
            if (asset.set != null)
                asset.set.remove(asset.ID);
        }
        managedAssetPool.free(asset);
    }

    private static final Pool<ManagedAsset> managedAssetPool = new Pool<ManagedAsset>(128) {
        @Override
        protected ManagedAsset newObject() {
            return new ManagedAsset();
        }
    };

    private static class ManagedAsset implements Pool.Poolable {
        String ID = "";
        ManagedAssetReference asset = null;

        List<String> set = null;
        ManagedAsset parent = null;
        final List<ManagedAsset> dependent = new ArrayList<>();

        private boolean fresh = true;
        private boolean canAge = false;
        private int[] disposeParams = empty;

        enum AssetType {
            NONE,
            TEXTURE,
            ATLAS,
            REGION
        }

        public void setAsset(String ID, Object o, AssetType type) {
            this.ID = ID;
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
        public void setNull(String ID) {
            this.ID = ID;
            asset = new LockedNullReference(this, referenceQueue);
            canAge = false;
        }

        public boolean canAge() {
            return canAge;
        }

        public boolean isFresh() {
            return parent != null ? parent.isFresh() : fresh;
        }

        public void age() {
            fresh = false;
        }

        public void refresh() {
            if ((fresh = (asset instanceof LockedNullReference || (asset.get() != null))) && parent != null)
                parent.refresh();
        }

        @SuppressWarnings("unchecked")
        public <T> T item() {
            return (T) asset.get();
        }

        //Dispose of texture, clear reference, make it old
        public void dispose() {
            for (ManagedAsset child : dependent) {
                child.parent = null;
                child.dispose();
            }
            dependent.clear();

            for (int handle : disposeParams) {
                if (handle != 0) {
                    Gdx.gl.glDeleteTexture(handle);
                }
            }

            parent = null;

            asset.clear();
            age();
        }

        @Override
        public void reset() {
            if (asset != null)
                asset.clear();
            ID = "";
            disposeParams = empty;

            fresh = true;
            canAge = false;

            set = null;
            parent = null;
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
        static class LockedNullReference extends ManagedAssetReference {
            private static final Object lock = new Object();
            public LockedNullReference(ManagedAsset holder, ReferenceQueue<? super Object> q) {
                super(holder, lock, q);
            }

            @Override
            public Object get() {
                return null;
            }
        }
    }
}

