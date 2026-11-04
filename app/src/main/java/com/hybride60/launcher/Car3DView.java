package com.hybride60.launcher;

import android.content.Context;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.opengl.Matrix;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.Filament;
import com.google.android.filament.LightManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.TransformManager;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;
import com.google.android.filament.gltfio.AssetLoader;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.gltfio.MaterialProvider;
import com.google.android.filament.gltfio.ResourceLoader;
import com.google.android.filament.gltfio.UbershaderProvider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** First Filament GLB test. Keep the old cube version on v2-3d. */
public class Car3DView extends SurfaceView implements SurfaceHolder.Callback {
    private Engine engine;
    private Renderer renderer;
    private Scene scene;
    private View view;
    private Camera camera;
    private SwapChain swapChain;
    private AssetLoader assetLoader;
    private ResourceLoader resourceLoader;
    private MaterialProvider materialProvider;
    private FilamentAsset asset;
    private int cameraEntity;
    private int lightEntity;

    private float yaw = -30f;
    private float pitch = 15f;
    private float lastX, lastY;
    private float modelScale = 1f;
    private final float[] center = new float[3];
    private boolean running;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            drawFrame();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public Car3DView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            
Filament.init();
com.google.android.filament.gltfio.Gltfio.init();
engine = Engine.create();

            renderer = engine.createRenderer();
            scene = engine.createScene();
            view = engine.createView();
            cameraEntity = EntityManager.get().create();
            camera = engine.createCamera(cameraEntity);
            view.setCamera(camera);
            view.setScene(scene);
// Fond noir opaque
setZOrderOnTop(false);
getHolder().setFormat(
    android.graphics.PixelFormat.OPAQUE
);

// Le rendu remplace les images précédentes
view.setBlendMode(View.BlendMode.OPAQUE);

// Effacement du fond à chaque frame
renderer.setClearOptions(
    new Renderer.ClearOptions()
);
            lightEntity = EntityManager.get().create();
            new LightManager.Builder(LightManager.Type.SUN)
                    .color(1f, 1f, 1f)
                    .intensity(100000f)
                    .direction(-0.5f, -1f, -0.7f)
                    .build(engine, lightEntity);
            scene.addEntity(lightEntity);
            swapChain = engine.createSwapChain(holder.getSurface());

            materialProvider = new UbershaderProvider(engine);
            assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
            resourceLoader = new ResourceLoader(engine);
android.util.Log.i("HybridE60", "Début du chargement GLB");
            ByteBuffer glb = readAsset("bmw_m5_e60.glb");
            asset = assetLoader.createAsset(glb);
            if (asset == null) throw new IllegalStateException("GLB non reconnu");
            resourceLoader.loadResources(asset);
            scene.addEntities(asset.getEntities());

            float[] c = asset.getBoundingBox().getCenter();
            float[] e = asset.getBoundingBox().getHalfExtent();
            System.arraycopy(c, 0, center, 0, 3);
            float longest = Math.max(e[0], Math.max(e[1], e[2]));
            modelScale = longest > 0.0001f ? 1.5f / longest : 1f;
            updateModel();
            updateCamera();
            running = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        
} catch (Throwable e) {
    android.util.Log.e("HybridE60", "Erreur Filament", e);
    android.widget.Toast.makeText(
        getContext(),
        "Erreur 3D : " + e.getClass().getSimpleName()
            + " : " + e.getMessage(),
        android.widget.Toast.LENGTH_LONG
    ).show();
}

        }
    

    private ByteBuffer readAsset(String name) throws Exception {
        try (InputStream in = getContext().getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[16384];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            byte[] data = out.toByteArray();
            ByteBuffer result = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
            result.put(data);
            result.flip();
            return result;
        }
    }

    private void updateModel() {
        if (asset == null || engine == null) return;
        float[] rotation = new float[16];
        Matrix.setIdentityM(rotation, 0);
        Matrix.rotateM(rotation, 0, pitch, 1f, 0f, 0f);
        Matrix.rotateM(rotation, 0, yaw, 0f, 1f, 0f);
        float[] normalization = new float[16];
        Matrix.setIdentityM(normalization, 0);
        Matrix.scaleM(normalization, 0, modelScale, modelScale, modelScale);
        Matrix.translateM(normalization, 0, -center[0], -center[1], -center[2]);
        float[] transform = new float[16];
        Matrix.multiplyMM(transform, 0, rotation, 0, normalization, 0);
        TransformManager tm = engine.getTransformManager();
        int instance = tm.getInstance(asset.getRoot());
        tm.setTransform(instance, transform);
    }

    private void updateCamera() {
        if (camera == null) return;
        camera.lookAt(0.0, 0.3, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        camera.setProjection(45.0, (double) w / h, 0.1, 100.0, Camera.Fov.VERTICAL);
    }

    private void drawFrame() {
        if (engine == null || swapChain == null) return;
        
if (renderer.beginFrame(swapChain, System.nanoTime())) {

            renderer.render(view);
            renderer.endFrame();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (view != null) view.setViewport(new Viewport(0, 0, width, height));
        updateCamera();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        // A later revision will add complete engine resource teardown.
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            yaw += (x - lastX) * 0.5f;
            pitch = Math.max(-45f, Math.min(45f, pitch + (y - lastY) * 0.3f));
            updateModel();
        }
        lastX = x;
        lastY = y;
        return true;
    }
}