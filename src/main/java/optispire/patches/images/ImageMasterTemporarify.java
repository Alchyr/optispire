package optispire.patches.images;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import javassist.*;
import javassist.bytecode.BadBytecode;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import optispire.Optispire;
import optispire.patches.DynamicPatchTrigger;
import org.clapper.util.classutil.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class ImageMasterTemporarify extends DynamicPatchTrigger.DynamicPatch {
    //Replace all read attempts to imagemaster textures with requests from TextureDisposingMap
    //Write attempts will not be replaced.
    //Also have a provider referring to original variable that will be returned if not null (modified externally?)

    //Texture t = ImageMaster.WOBBLY_ORB_VFX  ->
    //Texture t = ImageMaster.WOBBLY_ORB_VFX != null ? ImageMaster.WOBBLY_ORB_VFX : some map.get("ImageMaster.WOBBLY_ORB_VFX")


    private final Set<String> texFields = new HashSet<>();
    private final Set<String> atlasFields = new HashSet<>();
    private final Set<String> atlasRegionFields = new HashSet<>();

    //Need to track info about these fields;
    //This will be used to modify all attempts to access them, other than in the ImageMaster class.
    //That modification is handled separately.

    public ImageMasterTemporarify() {
        Field[] imageMasterFields = ImageMaster.class.getDeclaredFields();

        //Note: This misses a few special cases that use arrays or arraylist or map and stuff
        for (Field f : imageMasterFields) {
            if (Modifier.isStatic(f.getModifiers())) {
                Class<?> type = f.getType();

                if (Texture.class.equals(type)) {
                    texFields.add(f.getName());
                }
                else if (TextureAtlas.class.equals(type)) {
                    atlasFields.add(f.getName());
                }
                else if (TextureAtlas.AtlasRegion.class.equals(type)) {
                    atlasRegionFields.add(f.getName());
                }
            }
        }
    }

    CtClass texture, textureAtlas, atlasRegion;
    @Override
    public void directPatch(ClassPool pool) throws NotFoundException, CannotCompileException {
        CtClass imageMaster = pool.get(ImageMaster.class.getName());
        textureAtlas = pool.get(TextureAtlas.class.getName());
        atlasRegion = pool.get(TextureAtlas.AtlasRegion.class.getName());
        texture = pool.get(Texture.class.getName());
        CtMethod initialize = imageMaster.getDeclaredMethod("initialize");

        initialize.instrument(new ImageMasterInitializeEditor(textureAtlas, atlasRegion, texture));
    }

    private ImageAccessModifier exprEditor = new ImageAccessModifier();
    @Override
    public boolean process(CtClass ctClass, ClassInfo classInfo) throws NotFoundException, CannotCompileException, BadBytecode {
        exprEditor.modified = false;
        ctClass.instrument(exprEditor);
        return exprEditor.modified;
    }

    private class ImageAccessModifier extends ExprEditor {
        boolean modified = false;

        @Override
        public void edit(FieldAccess f) throws CannotCompileException {
            if (f.isReader()) {
                String name = f.getFieldName();

                try {
                    if (texFields.contains(name) && f.getField().getType().equals(texture)) {
                        modified = true;
                        f.replace("$_ = " + Optispire.class.getName() + ".getTexture($proceed($$), \"IMAGEMASTER:" + name + "\");");
                    }
                    else if (atlasFields.contains(name) && f.getField().getType().equals(textureAtlas)) {
                        modified = true;
                        f.replace("$_ = " + Optispire.class.getName() + ".getAtlas($proceed($$), \"IMAGEMASTER:" + name + "\");");

                    }
                    else if (atlasRegionFields.contains(name) && f.getField().getType().equals(atlasRegion)) {
                        modified = true;
                        f.replace("$_ = " + Optispire.class.getName() + ".getRegion($proceed($$), \"IMAGEMASTER:" + name + "\");");
                    }
                } catch (NotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }








    //This stuff occurs during runtime
    private static String currentAtlas = null;
    public static Object receiveAtlasAttempt(FileHandle atlasFile) {
        return atlasFile;
    }
    public static void trackAtlasName(String atlasVar, Object atlasFile) {
        if (!(atlasFile instanceof FileHandle)) {
            throw new RuntimeException("trackAtlasName did not receive a FileHandle, received " + atlasFile);
        }

        currentAtlas = "IMAGEMASTER:" + atlasVar;
        Optispire.registerAtlas(currentAtlas, (FileHandle) atlasFile);
    }
    public static Object receiveRegionAttempt(String regionName) {
        return regionName;
    }
    public static void trackRegion(String regionVar, Object regionName) {
        if (!(regionName instanceof String)) {
            throw new RuntimeException("trackRegion did not receive a String regionName, received " + regionName);
        }

        Optispire.registerRegion("IMAGEMASTER:" + regionVar, currentAtlas, (String) regionName);
    }

    private static final HashSet<String> excludes = new HashSet<>();
    static {
        //maybe come back to handle arrays/lists properly later.
        excludes.add("images/vfx/petal/petal1.png");
        excludes.add("images/vfx/petal/petal2.png");
        excludes.add("images/vfx/petal/petal3.png");
        excludes.add("images/vfx/petal/petal4.png");
        excludes.add("images/vfx/water_drop/drop1.png");
        excludes.add("images/vfx/water_drop/drop2.png");
        excludes.add("images/vfx/water_drop/drop3.png");
        excludes.add("images/vfx/water_drop/drop4.png");
        excludes.add("images/vfx/water_drop/drop5.png");
        excludes.add("images/vfx/water_drop/drop6.png");
        excludes.add("images/vfx/defect/lightning_passive_1.png");
        excludes.add("images/vfx/defect/lightning_passive_2.png");
        excludes.add("images/vfx/defect/lightning_passive_3.png");
        excludes.add("images/vfx/defect/lightning_passive_4.png");
        excludes.add("images/vfx/defect/lightning_passive_5.png");
    }
    public static Object receiveTextureAttempt(String texturePath) {
        if (excludes.contains(texturePath))
            return ImageMaster.loadImage(texturePath);
        return texturePath;
    }
    public static void trackTexture(String textureVar, Object texturePath) {
        if (!(texturePath instanceof String)) {
            throw new RuntimeException("trackTexture did not receive a String texturePath, received " + textureVar + ", " + texturePath);
        }

        Optispire.registerTexture("IMAGEMASTER:" + textureVar, (String) texturePath);
    }


    private static class ImageMasterInitializeEditor extends ExprEditor {
        private final CtClass textureAtlasClz, atlasRegionClz, textureClz;

        public ImageMasterInitializeEditor(CtClass textureAtlasClz, CtClass atlasRegionClz, CtClass textureClz) {
            this.textureAtlasClz = textureAtlasClz;
            this.atlasRegionClz = atlasRegionClz;
            this.textureClz = textureClz;
        }
        //modify image master initialization, determine connections between atlas and atlas regions

        @Override
        public void edit(FieldAccess f) throws CannotCompileException {
            try {
                if (f.isStatic() && f.isWriter() && f.getClassName().equals(ImageMaster.class.getName())) {
                    CtField field = f.getField();
                    CtClass fieldType = field.getType();
                    if (fieldType.equals(textureAtlasClz)) {
                        //Assignment of a new TextureAtlas
                        //At this point, code should be either
                        // atlas = receiveAtlasAttempt(new FileHandle("asdf"));
                        // atlas = new TextureAtlas(new FileHandle("asdf"));

                        //Goal is to change to atlas = null;
                        String varName = field.getName();
                        f.replace(ImageMasterTemporarify.class.getName() + ".trackAtlasName(\"" + varName + "\", $1);");
                    }
                    else if (fieldType.equals(atlasRegionClz)) {
                        //code should be either
                        // region = atlas.findRegion("asdf");
                        // region = receiveRegionAttempt("asdf");

                        String varName = field.getName();
                        f.replace(ImageMasterTemporarify.class.getName() + ".trackRegion(\"" + varName + "\", $1);");
                    }
                    else if (fieldType.equals(textureClz)) {
                        //code should be either
                        // tex = loadImage("asdf");
                        // tex = receiveTextureAttempt("asdf");

                        String varName = field.getName();
                        f.replace(ImageMasterTemporarify.class.getName() + ".trackTexture(\"" + varName + "\", $1);");
                    }
                }
            } catch (NotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }

        @Override
        public void edit(NewExpr e) throws CannotCompileException {
            if (TextureAtlas.class.getName().equals(e.getClassName())) {
                //new atlas
                e.replace(
                        "$_ = " + ImageMasterTemporarify.class.getName() + ".receiveAtlasAttempt($1);"
                );
            }
            else if (Texture.class.getName().equals(e.getClassName())) {
                //new texture
                e.replace(
                        "$_ = " + ImageMasterTemporarify.class.getName() + ".receiveTextureAttempt($1);"
                );
            }
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            if (m.getClassName().equals(TextureAtlas.class.getName()) && m.getMethodName().equals("findRegion")) {
                m.replace(
                        "$_ = " + ImageMasterTemporarify.class.getName() + ".receiveRegionAttempt($1);"
                );
            }
            else if (m.getClassName().equals(ImageMaster.class.getName()) && m.getMethodName().equals("loadImage")) {
                m.replace(
                        "$_ = " + ImageMasterTemporarify.class.getName() + ".receiveTextureAttempt($1);"
                );
            }
        }
    }
}
