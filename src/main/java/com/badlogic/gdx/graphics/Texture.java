package com.badlogic.gdx.graphics;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import optispire.RamSaver;

import java.util.*;
import java.util.function.Consumer;

public class Texture extends GLTexture {
    private static final Set<String> imageExtensions = new HashSet<>();
    static {
        imageExtensions.add("jpg");
        imageExtensions.add("jpeg");
        imageExtensions.add("png");
        imageExtensions.add("cim");
        imageExtensions.add("etc1");
        imageExtensions.add("ktx");
        imageExtensions.add("zktx");
        imageExtensions.add("bmp"); //do these even work in libgdx
        imageExtensions.add("gif");
        imageExtensions.add("sff"); //wtf vupshion
    }

    TextureData data;

    public final boolean isFake;
    public FileHandle file = null;
    public boolean useMipMaps = false;
    public Pixmap.Format format = null;

    public Texture(String internalPath) {
        this(Gdx.files.internal(internalPath));
    }

    public Texture(FileHandle file) {
        this(file, null, false);
    }

    public Texture(FileHandle file, boolean useMipMaps) {
        this(file, null, useMipMaps);
    }

    public Texture(Pixmap pixmap) {
        this(new PixmapTextureData(pixmap, null, false, false));
    }

    public Texture(Pixmap pixmap, boolean useMipMaps) {
        this(new PixmapTextureData(pixmap, null, useMipMaps, false));
    }

    public Texture(Pixmap pixmap, Pixmap.Format format, boolean useMipMaps) {
        this(new PixmapTextureData(pixmap, format, useMipMaps, false));
    }

    public Texture(int width, int height, Pixmap.Format format) {
        this(new PixmapTextureData(new Pixmap(width, height, format), null, false, true));
    }

    public Texture(TextureData data) {
        this(3553, Gdx.gl.glGenTexture(), data);
    }

    //Useless constructor for filepath textures
    public Texture(FileHandle file, Pixmap.Format format, boolean useMipMaps) {
        //this(TextureData.Factory.loadFromFile(file, format, useMipMaps));
        super(GL20.GL_TEXTURE_2D, 0);
        this.data = placeholderData;
        this.isFake = true;

        this.file = file;
        this.format = format;
        this.useMipMaps = useMipMaps;

        //Act like a normal texture, throwing an exception if unable to load
        if (!file.exists() || !imageExtensions.contains(file.extension().toLowerCase())) {
            throw new GdxRuntimeException("Couldn't load file: " + file);
        }

        if (!RamSaver.textureExists(file.path())) {
            RamSaver.registerTexture(file.path(), new RamSaver.FileTextureSupplier(file, format, useMipMaps));
        }
    }

    //Not Useless constructor, used by self
    private Texture(int glTarget, int glHandle, TextureData data) {
        super(glTarget, glHandle);
        this.isFake = false;
        //System.out.println("Tex (not file)");
        this.load(data);
        if (data.isManaged()) {
            addManagedTexture(Gdx.app, this);
        }
    }
    //Not Useless constructor, used by RealTexture
    protected Texture(int glTarget, int glHandle) {
        super(glTarget, glHandle);
        this.isFake = false;
    }


    public Texture getRealTexture() {
        if (!isFake)
            return this;
        
        return RamSaver.getTexture(null, file.path());
    }


    public void load(TextureData data) {
        if (!isFake) {
            if (this.data != null && data.isManaged() != this.data.isManaged()) {
                throw new GdxRuntimeException("New data must have the same managed status as the old data");
            } else {
                this.data = data;
                if (!data.isPrepared()) {
                    data.prepare();
                }

                this.bind();
                uploadImageData(3553, data);
                this.setFilter(this.minFilter, this.magFilter);
                this.setWrap(this.uWrap, this.vWrap);
                Gdx.gl.glBindTexture(this.glTarget, 0);
            }
        }
    }

    @Override
    protected void reload() {
        if (!this.isFake) {
            if (!this.isManaged()) {
                throw new GdxRuntimeException("Tried to reload unmanaged Texture");
            } else {
                this.glHandle = Gdx.gl.glGenTexture();
                this.load(this.data);
            }
        }
        else {
            Texture t = RamSaver.getTexture(null, file.path());
            t.reload();
        }
    }

    public TextureData getTextureData() {
        if (isFake) {
            Texture t = RamSaver.getTexture(null, file.path());
            return t.getTextureData();
        }
        else {
            return data;
        }
    }

    public void draw(Pixmap pixmap, int x, int y) {
        if (isFake) {
            Texture t = RamSaver.getTexture(null, file.path());
            t.draw(pixmap, x, y);
        }
        else {
            if (this.data.isManaged()) {
                throw new GdxRuntimeException("can't draw to a managed texture");
            } else {
                this.bind();
                Gdx.gl.glTexSubImage2D(this.glTarget, 0, x, y, pixmap.getWidth(), pixmap.getHeight(), pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels());
            }
        }
    }

    boolean knowSize = false;
    int width;
    int height;
    public int getWidth() {
        if (!isFake) {
            return data.getWidth();
        }
        if (!knowSize) {
            getSize(this);
        }
        return width;
    }

    @Override
    public int getHeight() {
        if (!isFake) {
            return data.getHeight();
        }
        if (!knowSize) {
            getSize(this);
        }
        return height;
    }

    public int getDepth() {
        return 0;
    }

    @Override
    public boolean isManaged() {
        if (!isFake) {
            return data.isManaged();
        }
        else {
            Texture t = RamSaver.getTexture(null, file.path());
            return t.isManaged();
        }
    }

    @Override
    public Texture.TextureFilter getMinFilter() {
        if (!isFake)
            return super.getMinFilter();

        Texture t = RamSaver.getExistingTexture(file.path());
        return t == null ? super.getMinFilter() : t.getMinFilter();
    }

    @Override
    public Texture.TextureFilter getMagFilter() {
        if (!isFake)
            return super.getMagFilter();

        Texture t = RamSaver.getExistingTexture(file.path());
        return t == null ? super.getMagFilter() : t.getMagFilter();
    }

    @Override
    public Texture.TextureWrap getUWrap() {
        if (!isFake)
            return super.getUWrap();

        Texture t = RamSaver.getExistingTexture(file.path());
        return t == null ? super.getUWrap() : t.getUWrap();
    }

    @Override
    public Texture.TextureWrap getVWrap() {
        if (!isFake)
            return super.getVWrap();

        Texture t = RamSaver.getExistingTexture(file.path());
        return t == null ? super.getVWrap() : t.getVWrap();
    }

    @Override
    public int getTextureObjectHandle() {
        if (!isFake)
            return super.getTextureObjectHandle();

        Texture t = RamSaver.getTexture(null, file.path());
        return t.getTextureObjectHandle();
    }

    @Override
    public void unsafeSetWrap(Texture.TextureWrap u, Texture.TextureWrap v, boolean force) {
        if (!isFake) {
            super.unsafeSetWrap(u, v, force);
            return;
        }

        if (u != null)
            this.uWrap = u;
        if (v != null)
            this.vWrap = v;

        String key = file.path();
        RamSaver.FileTextureSupplier supplier = RamSaver.getTextureSupplier(key);
        if (supplier != null)
            supplier.setWrap(u, v);

        Texture t = RamSaver.getExistingTexture(key);
        if (t != null)
            t.unsafeSetWrap(u, v, force);
    }

    @Override
    public void setWrap(Texture.TextureWrap u, Texture.TextureWrap v) {
        if (!isFake) {
            super.setWrap(u, v);
            return;
        }

        this.uWrap = u;
        this.vWrap = v;

        String key = file.path();
        RamSaver.FileTextureSupplier supplier = RamSaver.getTextureSupplier(key);
        if (supplier != null)
            supplier.setWrap(u, v);

        Texture t = RamSaver.getExistingTexture(key);
        if (t != null)
            t.setWrap(u, v);
    }

    @Override
    public void unsafeSetFilter(Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, boolean force) {
        if (!isFake) {
            super.unsafeSetFilter(minFilter, magFilter, force);
            return;
        }

        if (minFilter != null)
            this.minFilter = minFilter;
        if (magFilter != null)
            this.magFilter = magFilter;

        String key = file.path();
        RamSaver.FileTextureSupplier supplier = RamSaver.getTextureSupplier(key);
        if (supplier != null)
            supplier.setFilter(minFilter, magFilter);

        Texture t = RamSaver.getExistingTexture(key);
        if (t != null)
            t.unsafeSetFilter(minFilter, magFilter, force);
    }

    @Override
    public void setFilter(Texture.TextureFilter minFilter, Texture.TextureFilter magFilter) {
        if (!isFake) {
            super.setFilter(minFilter, magFilter);
            return;
        }

        this.minFilter = minFilter;
        this.magFilter = magFilter;

        String key = file.path();
        RamSaver.FileTextureSupplier supplier = RamSaver.getTextureSupplier(key);
        if (supplier != null)
            supplier.setFilter(minFilter, magFilter);

        Texture t = RamSaver.getExistingTexture(key);
        if (t != null)
            t.setFilter(minFilter, magFilter);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (!isFake && file != null) {
            RamSaver.dispose(file.path());
        }
    }

    private static final FakeData placeholderData = new FakeData();
    private static class FakeData implements TextureData {
        @Override
        public TextureDataType getType() {
            return TextureDataType.Custom;
        }

        @Override
        public boolean isPrepared() {
            return false;
        }

        @Override
        public void prepare() {

        }

        @Override
        public Pixmap consumePixmap() {
            return null;
        }

        @Override
        public boolean disposePixmap() {
            return false;
        }

        @Override
        public void consumeCustomData(int i) {

        }

        @Override
        public int getWidth() {
            return 1;
        }

        @Override
        public int getHeight() {
            return 1;
        }

        @Override
        public Pixmap.Format getFormat() {
            return Pixmap.Format.RGBA8888;
        }

        @Override
        public boolean useMipMaps() {
            return false;
        }

        @Override
        public boolean isManaged() {
            return false;
        }
    }














    //************************STATICS**************************
    private static AssetManager assetManager;
    static final Map<Application, Array<Texture>> managedTextures = new HashMap<>();

    protected static void addManagedTexture(Application app, Texture texture) {
        Array<Texture> managedTextureArray = managedTextures.get(app);
        if (managedTextureArray == null) {
            managedTextureArray = new Array<>();
        }

        managedTextureArray.add(texture);
        managedTextures.put(app, managedTextureArray);
    }

    public static void clearAllTextures(Application app) {
        managedTextures.remove(app);
    }

    public static void invalidateAllTextures(Application app) {
        Array<Texture> managedTextureArray = managedTextures.get(app);
        if (managedTextureArray != null) {
            if (assetManager == null) {
                for(int i = 0; i < managedTextureArray.size; ++i) {
                    Texture texture = managedTextureArray.get(i);
                    texture.reload();
                }
            } else {
                assetManager.finishLoading();
                Array<Texture> textures = new Array<>(managedTextureArray);

                for (Texture texture : textures) {
                    String fileName = assetManager.getAssetFileName(texture);
                    if (fileName == null) {
                        texture.reload();
                    } else {
                        final int refCount = assetManager.getReferenceCount(fileName);
                        assetManager.setReferenceCount(fileName, 0);
                        texture.glHandle = 0;
                        TextureLoader.TextureParameter params = new TextureLoader.TextureParameter();
                        params.textureData = texture.getTextureData();
                        params.minFilter = texture.getMinFilter();
                        params.magFilter = texture.getMagFilter();
                        params.wrapU = texture.getUWrap();
                        params.wrapV = texture.getVWrap();
                        params.genMipMaps = texture.data.useMipMaps();
                        params.texture = texture;
                        params.loadedCallback = new AssetLoaderParameters.LoadedCallback() {
                            public void finishedLoading(AssetManager assetManager, String fileName, Class type) {
                                assetManager.setReferenceCount(fileName, refCount);
                            }
                        };
                        assetManager.unload(fileName);
                        texture.glHandle = Gdx.gl.glGenTexture();
                        assetManager.load(fileName, Texture.class, params);
                    }
                }

                managedTextureArray.clear();
                managedTextureArray.addAll(textures);
            }

        }
    }

    public static void setAssetManager(AssetManager manager) {
        assetManager = manager;
    }

    public static String getManagedStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append("Managed textures/app: { ");

        for (Application app : managedTextures.keySet()) {
            builder.append(managedTextures.get(app).size);
            builder.append(" ");
        }

        builder.append("}");
        return builder.toString();
    }

    public static int getNumManagedTextures() {
        return ((Array)managedTextures.get(Gdx.app)).size;
    }

    public enum TextureWrap {
        MirroredRepeat(33648),
        ClampToEdge(33071),
        Repeat(10497);

        final int glEnum;

        TextureWrap(int glEnum) {
            this.glEnum = glEnum;
        }

        public int getGLEnum() {
            return this.glEnum;
        }
    }

    public enum TextureFilter {
        Nearest(9728),
        Linear(9729),
        MipMap(9987),
        MipMapNearestNearest(9984),
        MipMapLinearNearest(9985),
        MipMapNearestLinear(9986),
        MipMapLinearLinear(9987);

        final int glEnum;

        TextureFilter(int glEnum) {
            this.glEnum = glEnum;
        }

        public boolean isMipMap() {
            return this.glEnum != 9728 && this.glEnum != 9729;
        }

        public int getGLEnum() {
            return this.glEnum;
        }
    }

    //--------------SIZE-----------------
    private static final byte[] PNG_HEADER = new byte[] { (byte)137, (byte)80, (byte)78, (byte)71, (byte)13, (byte)10, (byte)26, (byte)10 };
    private static final byte[] IHDR = new byte[] { (byte)73, (byte)72, (byte)68, (byte)82 };
    private static final byte[] KTX_HEADER = new byte[] {
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
    };


    private static final byte[] filedata = new byte[64];
    private static final Map<String, Consumer<Texture>> sizeGetters = new HashMap<>();
    static {
        sizeGetters.put("png", (t)->{
            int read = t.file.readBytes(filedata, 0, filedata.length);
            int i;
            //First 8 bytes, png header
            if (read > 26 && matches(0, PNG_HEADER)) {
                i = 8;
                while (i < read - 16) {
                    int nextChunk = i + 12 + readInt(i);
                    i += 4;
                    readIHDR(t, i);
                    i = nextChunk;
                    if (t.knowSize)
                        return;
                }
            }
        });
        sizeGetters.put("ktx", (t)->{
            int read = t.file.readBytes(filedata, 0, filedata.length);
            int i;
            //First 12 bytes, ktx header
            boolean endian = true;
            if (read > 44 && matches(0, KTX_HEADER)) {
                i = 12;
                if (readInt(i) == 0x04030201) {
                    endian = false;
                }
                i += 24;
                t.width = readInt(i, endian);
                i += 4;
                t.height = readInt(i, endian);
                t.knowSize = true;
            }
        });
    }
    private static void getSize(Texture t) {
        if (!t.isFake) {
            t.knowSize = true;
            t.width = t.getWidth();
            t.height = t.getHeight();
            return;
        }
        //swap to switch if adding more
        if (t.file != null) {
            Consumer<Texture> sizeGetter = sizeGetters.get(t.file.extension().toLowerCase());
            if (sizeGetter != null) {
                sizeGetter.accept(t);
                //If failed to process, continue to backup method.
                if (t.knowSize)
                    return;
            }
        }

        Texture real = t.getRealTexture();
        t.width = real.getWidth();
        t.height = real.getHeight();
        t.knowSize = true;
        t.dispose();
    }

    private static boolean matches(int fromIndex, byte[] toMatch) {
        for (int i=0; i < toMatch.length; ++i)
            if (Texture.filedata[fromIndex + i] != toMatch[i])
                return false;

        return true;
    }

    private static int readInt(int index) {
        return readInt(index, true);
    }
    private static int readInt(int index, boolean endian) {
        if (endian) {
            return (((Texture.filedata[index])            << 24) |
                    ((Texture.filedata[index + 1] & 0xff) << 16) |
                    ((Texture.filedata[index + 2] & 0xff) <<  8) |
                    ((Texture.filedata[index + 3] & 0xff)));
        }
        return (((Texture.filedata[index + 3] & 0xff) << 24) |
                ((Texture.filedata[index + 2] & 0xff) << 16) |
                ((Texture.filedata[index + 1] & 0xff) <<  8) |
                ((Texture.filedata[index] & 0xff)));
    }


    //png
    private static void readIHDR(Texture t, int index) {
        //Now to read chunk
        //First, 4 byte integer chunk length (8)
        //Then, 4 byte chunk type (12)
        //Then, the data
        for (int i = 0; i < IHDR.length; ++i) {
            if (Texture.filedata[index + i] != IHDR[i])
                return;
        }

        int width, height; //Technically speaking, unsigned int, but if someone has a 1 in the first bit of size, this mod is not for them or anyone else
        width = readInt(index + 4);
        height = readInt(index + 8);
        t.width = width;
        t.height = height;
        t.knowSize = true;
    }
}
