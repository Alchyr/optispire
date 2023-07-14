package com.badlogic.gdx.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.GdxRuntimeException;
import optispire.RamSaver;

public class RealTexture extends Texture {
    public RealTexture(String internalPath) {
        this(Gdx.files.internal(internalPath));
    }

    public RealTexture(FileHandle file) {
        this(file, null, false);
    }

    public RealTexture(FileHandle file, boolean useMipMaps) {
        this(file, null, useMipMaps);
    }

    public RealTexture(FileHandle file, Pixmap.Format format, boolean useMipMaps) {
        this(TextureData.Factory.loadFromFile(file, format, useMipMaps));
    }

    public RealTexture(Pixmap pixmap) {
        this(new PixmapTextureData(pixmap, null, false, false));
    }

    public RealTexture(Pixmap pixmap, boolean useMipMaps) {
        this(new PixmapTextureData(pixmap, null, useMipMaps, false));
    }

    public RealTexture(Pixmap pixmap, Pixmap.Format format, boolean useMipMaps) {
        this(new PixmapTextureData(pixmap, format, useMipMaps, false));
    }

    public RealTexture(int width, int height, Pixmap.Format format) {
        this(new PixmapTextureData(new Pixmap(width, height, format), null, false, true));
    }

    public RealTexture(TextureData data) {
        this(3553, Gdx.gl.glGenTexture(), data);
    }

    protected RealTexture(int glTarget, int glHandle, TextureData data) {
        super(glTarget, glHandle);
        this.load(data);
        if (data.isManaged()) {
            addManagedTexture(Gdx.app, this);
        }
    }

    public void load(TextureData data) {
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

    protected void reload() {
        if (!this.isManaged()) {
            throw new GdxRuntimeException("Tried to reload unmanaged Texture");
        } else {
            this.glHandle = Gdx.gl.glGenTexture();
            this.load(this.data);
        }
    }

    public void draw(Pixmap pixmap, int x, int y) {
        if (this.data.isManaged()) {
            throw new GdxRuntimeException("can't draw to a managed texture");
        } else {
            this.bind();
            Gdx.gl.glTexSubImage2D(this.glTarget, 0, x, y, pixmap.getWidth(), pixmap.getHeight(), pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels());
        }
    }

    public int getWidth() {
        return this.data.getWidth();
    }

    public int getHeight() {
        return this.data.getHeight();
    }

    public TextureData getTextureData() {
        return this.data;
    }

    public boolean isManaged() {
        return this.data.isManaged();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (file != null)
            RamSaver.dispose(file.path());
    }
}
