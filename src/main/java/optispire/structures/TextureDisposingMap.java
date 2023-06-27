package optispire.structures;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

//A map that holds weak references to textures
//When they are no longer referenced, they are disposed.
public class TextureDisposingMap {
    private final ReferenceQueue<Texture> queue = new ReferenceQueue<>();

    private Map<String, DisposableWeakTexture> values = new HashMap<>(256);

    public Texture get(String key) {
        DisposableWeakTexture maybeT = values.get(key);
        if (maybeT != null) {
            Texture t = maybeT.get();
            if (t != null) {
                return t;
            }
            else {
                maybeT.dispose();
            }
        }
        //Load texture and store
        /*Texture t = ;
        DisposableWeakTexture newVal = new DisposableWeakTexture();*/
        return null;//t;
    }

    private class DisposableWeakTexture extends WeakReference<Texture> {
        final String key;
        int handle;

        public DisposableWeakTexture(String key, Texture referent, ReferenceQueue<? super Texture> q) {
            super(referent, q);

            this.key = key;
            handle = referent.getTextureObjectHandle();
            //Managed textures will never be deleted through this, as they will have a reference.
        }

        public void dispose() {
            if (handle != 0) {
                Gdx.gl.glDeleteTexture(handle);
                handle = 0;
            }
        }
    }
}
