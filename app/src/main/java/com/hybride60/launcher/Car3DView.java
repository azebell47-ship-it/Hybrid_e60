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

/** HYBRID_E60: orbit camera, pinch zoom, gentle inertia; retain black framebuffer clear. */
public class Car3DView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "HybridE60";
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
    private int fillLightEntity;

    private float yaw = -30f;
    private float pitch = 15f;
    private float lastX, lastY;
    private float orbitRadius = 5f;
    private float previousPinchDistance;
    private boolean pinching;
    private float yawVelocity, pitchVelocity;
    private long lastFrameNanos;
    private float modelScale = 1f;
    private final float[] center = new float[3];
    private boolean running;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            if (lastFrameNanos != 0L) {
                float dt = Math.min(0.05f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f);
                if (!pinching && Math.abs(yawVelocity) + Math.abs(pitchVelocity) > 0.02f) {
                    yaw += yawVelocity * dt;
                    pitch = clamp(pitch + pitchVelocity * dt, -65f, 65f);
                    float damping = (float) Math.exp(-5.5f * dt);
                    yawVelocity *= damping;
                    pitchVelocity *= damping;
                    updateCamera();
                } else {
                    yawVelocity = 0f;
                    pitchVelocity = 0f;
                }
            }
            lastFrameNanos = frameTimeNanos;
            drawFrame(frameTimeNanos);
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
                com.google.android.filament.Filament.init();
                com.google.android.filament.gltfio.Gltfio.init();
                engine = Engine.create();
                renderer = engine.createRenderer();
                // IMPORTANT: this explicit clear fixes the persistent blue sphere.
                Renderer.ClearOptions clearOptions = new Renderer.ClearOptions();
                clearOptions.clear = true;
                clearOptions.discard = true;
                clearOptions.clearColor = new float[]{0f, 0f, 0f, 1f};
                renderer.setClearOptions(clearOptions);

                scene = engine.createScene();
                view = engine.createView();
                cameraEntity = EntityManager.get().create();
                camera = engine.createCamera(cameraEntity);
                view.setCamera(camera);
                view.setScene(scene);
                view.setBlendMode(View.BlendMode.OPAQUE);
                // Keep post-processing disabled, as in the working version.
                view.setPostProcessingEnabled(false);

                lightEntity = EntityManager.get().create();
                new LightManager.Builder(LightManager.Type.SUN)
                        .color(1f, 1f, 1f)
                        .intensity(110000f)
                        .direction(-0.5f, -1f, -0.7f)
                        .build(engine, lightEntity);
                scene.addEntity(lightEntity);

                // Gentle secondary light for better visibility on the dark-blue body.
                fillLightEntity = EntityManager.get().create();
                new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                        .color(0.8f, 0.9f, 1f)
                        .intensity(30000f)
                        .direction(0.7f, -0.4f, 0.5f)
                        .build(engine, fillLightEntity);
                scene.addEntity(fillLightEntity);

                materialProvider = new UbershaderProvider(engine);
                assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
                resourceLoader = new ResourceLoader(engine);
                asset = assetLoader.createAsset(readAsset("bmw_m5_e60_gris_test.glb"));
                if (asset == null) throw new IllegalStateException("GLB non reconnu");
                resourceLoader.loadResources(asset);
                scene.addEntities(asset.getEntities());

                float[] c = asset.getBoundingBox().getCenter();
                float[] e = asset.getBoundingBox().getHalfExtent();
                System.arraycopy(c, 0, center, 0, 3);
                float longest = Math.max(e[0], Math.max(e[1], e[2]));
                modelScale = longest > 0.0001f ? 1.5f / longest : 1f;
                updateModel();
            }
            swapChain = engine.createSwapChain(holder.getSurface());
            updateCamera();
            running = true;
            lastFrameNanos = 0L;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } catch (Throwable error) {
            running = false;
            Log.e(TAG, "Erreur Filament", error);
            Toast.makeText(getContext(), "Erreur 3D : " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private ByteBuffer readAsset(String name) throws Exception {
        try (InputStream in = getContext().getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            byte[] bytes = out.toByteArray();
            ByteBuffer result = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            result.put(bytes);
            result.flip();
            return result;
        }
    }

    private void updateModel() {
        if (asset == null || engine == null) return;
        float[] normalization = new float[16];
        Matrix.setIdentityM(normalization, 0);
        Matrix.scaleM(normalization, 0, modelScale, modelScale, modelScale);
        Matrix.translateM(normalization, 0, -center[0], -center[1], -center[2]);
        TransformManager manager = engine.getTransformManager();
        int instance = manager.getInstance(asset.getRoot());
        if (instance != 0) manager.setTransform(instance, normalization);
    }

    private void updateCamera() {
        if (camera == null) return;
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cx = orbitRadius * Math.sin(yr) * Math.cos(pr);
        double cy = orbitRadius * Math.sin(pr);
        double cz = orbitRadius * Math.cos(yr) * Math.cos(pr);
        camera.lookAt(cx, cy, cz, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        camera.setProjection(45.0, (double) width / height, 0.1, 100.0, Camera.Fov.VERTICAL);
    }

    private void drawFrame(long frameTimeNanos) {
        if (engine == null || renderer == null || swapChain == null || view == null) return;
        if (renderer.beginFrame(swapChain, frameTimeNanos)) {
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
        if (engine != null && swapChain != null) {
            engine.flushAndWait();
            engine.destroySwapChain(swapChain);
            swapChain = null;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float pointerDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                yawVelocity = 0f;
                pitchVelocity = 0f;
                pinching = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    pinching = true;
                    previousPinchDistance = pointerDistance(event);
                    yawVelocity = 0f;
                    pitchVelocity = 0f;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    float distance = pointerDistance(event);
                    if (previousPinchDistance > 10f && distance > 10f) {
                        orbitRadius = clamp(orbitRadius * previousPinchDistance / distance, 2.8f, 10f);
                        updateCamera();
                    }
                    previousPinchDistance = distance;
                    pinching = true;
                } else if (!pinching) {
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - lastX;
                    float dy = y - lastY;
                    yaw += dx * 0.5f;
                    pitch = clamp(pitch + dy * 0.3f, -65f, 65f);
                    // Velocity is measured in degrees per second, then decays after release.
                    long historyTime = event.getHistorySize() > 0
                            ? event.getHistoricalEventTime(event.getHistorySize() - 1)
                            : event.getEventTime() - 16;
                    float dt = Math.max(0.008f, (event.getEventTime() - historyTime) / 1000f);
                    yawVelocity = clamp(dx * 0.5f / dt, -240f, 240f);
                    pitchVelocity = clamp(dy * 0.3f / dt, -150f, 150f);
                    lastX = x;
                    lastY = y;
                    updateCamera();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pinching = false;
                yawVelocity = 0f;
                pitchVelocity = 0f;
                if (event.getPointerCount() >= 2) {
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                }
                return true;
            case MotionEvent.ACTION_UP:
                pinching = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                pinching = false;
                yawVelocity = 0f;
                pitchVelocity = 0f;
                return true;
            default:
                return true;
        }
    }
}
