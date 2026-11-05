package com.hybride60.launcher;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
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

/** HYBRID_E60 - Filament 1.54.5; no KTX loader required. */
public class Car3DView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "HybridE60";
    private static final String MODEL_FILE = "bmw_e60_gris_corrige.glb";

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
    private IndirectLight ambient;
    private int cameraEntity;
    private int cameraLightEntity;
    private final int[] fixedLights = new int[6];

    private float yaw = -55f, pitch = 12f, orbitRadius = 3.8f;
    private float lastX, lastY, previousPinchDistance;
    private long lastTouchTime, lastFrameNanos;
    private float yawVelocity, pitchVelocity;
    private boolean touching, pinching, running;
    private float modelScale = 1f;
    private final float[] center = new float[3];

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            if (!running) return;
            if (lastFrameNanos != 0L) {
                float dt = Math.min(0.05f, (nanos - lastFrameNanos) / 1_000_000_000f);
                if (!touching && !pinching && Math.abs(yawVelocity) + Math.abs(pitchVelocity) > 0.02f) {
                    yaw += yawVelocity * dt;
                    pitch = clamp(pitch + pitchVelocity * dt, -55f, 65f);
                    float damping = (float) Math.exp(-5.5f * dt);
                    yawVelocity *= damping;
                    pitchVelocity *= damping;
                    updateCamera();
                } else if (!touching && !pinching) {
                    yawVelocity = pitchVelocity = 0f;
                }
            }
            lastFrameNanos = nanos;
            drawFrame(nanos);
            if (running) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public Car3DView(Context context) {
        super(context);
        getHolder().setFormat(PixelFormat.OPAQUE);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (engine == null) {
                initializeFilament();
                initializeLighting();
                loadCarModel();
            }
            if (swapChain == null) swapChain = engine.createSwapChain(holder.getSurface());
            updateCamera();
            running = true;
            lastFrameNanos = 0L;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } catch (Throwable e) {
            running = false;
            Log.e(TAG, "3D initialization failed", e);
            Toast.makeText(getContext(), "BMW 3D : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeFilament() {
        com.google.android.filament.Filament.init();
        com.google.android.filament.gltfio.Gltfio.init();
        engine = Engine.create();
        renderer = engine.createRenderer();
        Renderer.ClearOptions clear = new Renderer.ClearOptions();
        clear.clear = true;
        clear.discard = true;
        clear.clearColor = new float[]{0.055f, 0.065f, 0.080f, 1f};
        renderer.setClearOptions(clear);
        scene = engine.createScene();
        view = engine.createView();
        cameraEntity = EntityManager.get().create();
        camera = engine.createCamera(cameraEntity);
        view.setCamera(camera);
        view.setScene(scene);
        view.setBlendMode(View.BlendMode.OPAQUE);
        view.setPostProcessingEnabled(true);
        // Fixed exposure: avoids automatic angle-dependent variations.
        camera.setExposure(16f, 1f / 125f, 100f);
    }

    private void initializeLighting() {
        // Diffuse ambient only: not a substitute for a prefiltered specular IBL.
        // Constant RGB irradiance represented by the first SH coefficient.
        float[] sh = new float[27];
        sh[0] = 0.55f / 0.282095f;
        sh[1] = 0.56f / 0.282095f;
        sh[2] = 0.58f / 0.282095f;
        ambient = new IndirectLight.Builder()
                .irradiance(3, sh)
                .intensity(850f)
                .build(engine);
        scene.setIndirectLight(ambient);

        fixedLights[0] = directional(9000f, -1f, -0.75f, 0f);
        fixedLights[1] = directional(9000f,  1f, -0.75f, 0f);
        fixedLights[2] = directional(5500f,  0f, -1f,    0f);
        fixedLights[3] = directional(6500f,  0f, -0.55f,-1f);
        fixedLights[4] = directional(6500f,  0f, -0.55f, 1f);
        fixedLights[5] = directional(3500f, -0.7f,-0.5f, 0.7f);
        cameraLightEntity = directional(3500f, 0f, -0.4f, -1f);
    }

    private int directional(float intensity, float dx, float dy, float dz) {
        int entity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(intensity)
                .direction(dx, dy, dz)
                .castShadows(false)
                .build(engine, entity);
        scene.addEntity(entity);
        return entity;
    }

    private void updateCameraLight(double cx, double cy, double cz) {
        if (engine == null || cameraLightEntity == 0) return;
        LightManager lm = engine.getLightManager();
        int instance = lm.getInstance(cameraLightEntity);
        if (instance == 0) return;
        float dx = (float) -cx, dy = (float) -cy, dz = (float) -cz;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.0001f) lm.setDirection(instance, dx / length, dy / length, dz / length);
    }

    private void loadCarModel() throws Exception {
        materialProvider = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
        resourceLoader = new ResourceLoader(engine);
        asset = assetLoader.createAsset(readAsset(MODEL_FILE));
        if (asset == null) throw new IllegalStateException("GLB invalide : " + MODEL_FILE);
        resourceLoader.loadResources(asset);
        scene.addEntities(asset.getEntities());
        float[] c = asset.getBoundingBox().getCenter();
        float[] e = asset.getBoundingBox().getHalfExtent();
        System.arraycopy(c, 0, center, 0, 3);
        float longest = Math.max(e[0], Math.max(e[1], e[2]));
        modelScale = longest > 0.0001f ? 1.5f / longest : 1f;
        updateModel();
    }

    private ByteBuffer readAsset(String filename) throws Exception {
        try (InputStream in = getContext().getAssets().open(filename);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            byte[] bytes = out.toByteArray();
            ByteBuffer bb = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            bb.put(bytes);
            bb.flip();
            return bb;
        }
    }

    private void updateModel() {
        if (asset == null || engine == null) return;
        float[] transform = new float[16];
        Matrix.setIdentityM(transform, 0);
        Matrix.scaleM(transform, 0, modelScale, modelScale, modelScale);
        Matrix.translateM(transform, 0, -center[0], -center[1], -center[2]);
        TransformManager tm = engine.getTransformManager();
        int instance = tm.getInstance(asset.getRoot());
        if (instance != 0) tm.setTransform(instance, transform);
    }

    private void updateCamera() {
        if (camera == null) return;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double cx = orbitRadius * Math.sin(yr) * Math.cos(pr);
        double cy = orbitRadius * Math.sin(pr);
        double cz = orbitRadius * Math.cos(yr) * Math.cos(pr);
        camera.lookAt(cx, cy, cz, 0, 0, 0, 0, 1, 0);
        int width = Math.max(1, getWidth()), height = Math.max(1, getHeight());
        camera.setProjection(40, (double) width / height, 0.1, 100, Camera.Fov.VERTICAL);
        updateCameraLight(cx, cy, cz);
    }

    private void drawFrame(long nanos) {
        if (engine == null || renderer == null || swapChain == null || view == null) return;
        if (renderer.beginFrame(swapChain, nanos)) {
            renderer.render(view);
            renderer.endFrame();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (view != null) view.setViewport(new Viewport(0, 0, Math.max(1, width), Math.max(1, height)));
        updateCamera();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        if (engine != null && swapChain != null) {
            engine.flushAndWait();
            engine.destroySwapChain(swapChain);
            swapChain = null;
        }
    }

    private static float clamp(float x, float low, float high) {
        return Math.max(low, Math.min(high, x));
    }

    private static float pointerDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX(); lastY = event.getY();
                lastTouchTime = event.getEventTime();
                yawVelocity = pitchVelocity = 0f;
                pinching = false; touching = true;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    pinching = true;
                    previousPinchDistance = pointerDistance(event);
                    yawVelocity = pitchVelocity = 0f;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    float distance = pointerDistance(event);
                    if (previousPinchDistance > 10f && distance > 10f) {
                        orbitRadius = clamp(orbitRadius * previousPinchDistance / distance, 2.5f, 9f);
                        updateCamera();
                    }
                    previousPinchDistance = distance;
                    pinching = true;
                } else if (!pinching) {
                    float x = event.getX(), y = event.getY();
                    float dx = x - lastX, dy = y - lastY;
                    long now = event.getEventTime();
                    float dt = Math.max(0.008f, (now - lastTouchTime) / 1000f);
                    yaw += dx * 0.5f;
                    pitch = clamp(pitch + dy * 0.3f, -55f, 65f);
                    yawVelocity = clamp(dx * 0.5f / dt, -240f, 240f);
                    pitchVelocity = clamp(dy * 0.3f / dt, -150f, 150f);
                    lastX = x; lastY = y; lastTouchTime = now;
                    updateCamera();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pinching = false;
                yawVelocity = pitchVelocity = 0f;
                if (event.getPointerCount() >= 2) {
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                    lastTouchTime = event.getEventTime();
                }
                return true;
            case MotionEvent.ACTION_UP:
                touching = false; pinching = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                touching = false; pinching = false;
                yawVelocity = pitchVelocity = 0f;
                return true;
            default:
                return true;
        }
    }
}
