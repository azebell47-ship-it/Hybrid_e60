package com.hybride60.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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
import com.google.android.filament.utils.KTX1Loader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Car3DView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "HybridE60";
    private static final String MODEL_FILE = "bmw_m5_e60_gris_test.glb";
    private static final String IBL_FILE = "RNF_default_env_ibl.ktx";
    private static final float IBL_INTENSITY = 55000f;
    private static final float CAMERA_LIGHT_INTENSITY = 14000f;

    // Passer a false pour retrouver instantanement le mode sans IBL.
    private static final boolean TRY_IBL = true;

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
    private IndirectLight indirectLight;
    private int cameraEntity;
    private int cameraLightEntity;
    private final int[] fixedLights = new int[3];

    private float yaw = -55f;
    private float pitch = 12f;
    private float orbitRadius = 3.8f;
    private float lastX, lastY;
    private long lastTouchTime;
    private float previousPinchDistance;
    private boolean pinching, touching;
    private float yawVelocity, pitchVelocity;
    private float modelScale = 1f;
    private final float[] center = new float[3];
    private boolean running;
    private long lastFrameNanos;
    private String iblError = null;
    private boolean diagnosticShown;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            if (lastFrameNanos != 0L) {
                float dt = Math.min(0.05f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f);
                if (!touching && !pinching &&
                        Math.abs(yawVelocity) + Math.abs(pitchVelocity) > 0.02f) {
                    yaw += yawVelocity * dt;
                    pitch = clamp(pitch + pitchVelocity * dt, -55f, 65f);
                    float damping = (float) Math.exp(-5.5f * dt);
                    yawVelocity *= damping;
                    pitchVelocity *= damping;
                    updateCamera();
                } else if (!touching && !pinching) {
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
                initializeFilament();
                initializeLights();
                loadCarModel();
                // L'echec IBL ne doit pas empecher la BMW de s'afficher.
                if (TRY_IBL) initializeIBLSafely();
            }
            if (swapChain == null) swapChain = engine.createSwapChain(holder.getSurface());
            updateCamera();
            running = true;
            lastFrameNanos = 0L;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            Choreographer.getInstance().postFrameCallback(frameCallback);
            showIblDiagnosticIfNeeded();
        } catch (Throwable error) {
            running = false;
            Log.e(TAG, "Erreur initialisation 3D", error);
            showDialog("ERREUR 3D", error.toString());
        }
    }

    private void initializeFilament() {
        com.google.android.filament.Filament.init();
        com.google.android.filament.gltfio.Gltfio.init();
        // Aucun System.loadLibrary avec un nom suppose.
        engine = Engine.create();
        renderer = engine.createRenderer();
        Renderer.ClearOptions options = new Renderer.ClearOptions();
        options.clear = true;
        options.discard = true;
        options.clearColor = new float[]{0f, 0f, 0f, 1f};
        renderer.setClearOptions(options);
        scene = engine.createScene();
        view = engine.createView();
        cameraEntity = EntityManager.get().create();
        camera = engine.createCamera(cameraEntity);
        view.setCamera(camera);
        view.setScene(scene);
        view.setBlendMode(View.BlendMode.OPAQUE);
        view.setPostProcessingEnabled(false);
    }

    private void initializeIBLSafely() {
        String stage = "lecture KTX";
        int size = 0;
        try {
            ByteBuffer buffer = readAsset(IBL_FILE);
            size = buffer.remaining();
            stage = "validation signature";
            byte[] signature = {(byte)0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31,
                    (byte)0xBB, 0x0D, 0x0A, 0x1A, 0x0A};
            if (size < 64) throw new IllegalArgumentException("KTX trop petit");
            for (int i = 0; i < signature.length; i++) {
                if (buffer.get(i) != signature[i])
                    throw new IllegalArgumentException("Signature KTX1 incorrecte");
            }
            stage = "KTX1Loader.createIndirectLight";
            IndirectLight candidate = KTX1Loader.INSTANCE.createIndirectLight(
                    engine, buffer, new KTX1Loader.Options());
            if (candidate == null) throw new IllegalStateException("IBL null");
            candidate.setIntensity(IBL_INTENSITY);
            scene.setIndirectLight(candidate);
            indirectLight = candidate;
            iblError = null;
            Log.i(TAG, "IBL charge avec succes");
        } catch (Throwable error) {
            // Y compris UnsatisfiedLinkError, sans faire tomber le rendu 3D.
            Log.e(TAG, "IBL non charge, eclairage direct conserve", error);
            iblError = "Fichier : " + IBL_FILE + "\nTaille : " + size +
                    " octets\nEtape : " + stage + "\n\n" + error.toString();
        }
    }

    private void showIblDiagnosticIfNeeded() {
        if (iblError == null || diagnosticShown) return;
        diagnosticShown = true;
        showDialog("IBL indisponible - BMW affichee", iblError +
                "\n\nLa BMW continue avec les 4 lumieres directes.");
    }

    private void showDialog(String title, String message) {
        post(() -> {
            if (getWindowToken() == null) return;
            try {
                new AlertDialog.Builder(getContext())
                        .setTitle(title).setMessage(message)
                        .setPositiveButton("OK", null).show();
            } catch (Throwable error) {
                Log.e(TAG, "Affichage diagnostic impossible", error);
            }
        });
    }

    private void initializeLights() {
        fixedLights[0] = createDirectionalLight(30000f, 1f, .98f, .96f, -.3f, -1f, -.3f);
        fixedLights[1] = createDirectionalLight(18000f, 1f, 1f, 1f, 1f, -.3f, 0f);
        fixedLights[2] = createDirectionalLight(18000f, .96f, .98f, 1f, -1f, -.3f, 0f);
        cameraLightEntity = createDirectionalLight(CAMERA_LIGHT_INTENSITY,
                1f, 1f, 1f, 0f, -.4f, -1f);
    }

    private int createDirectionalLight(float intensity, float red, float green,
                                       float blue, float dx, float dy, float dz) {
        int entity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(red, green, blue).intensity(intensity)
                .direction(dx, dy, dz).castShadows(false).build(engine, entity);
        scene.addEntity(entity);
        return entity;
    }

    private void updateCameraLight(double cx, double cy, double cz) {
        if (engine == null || cameraLightEntity == 0) return;
        LightManager manager = engine.getLightManager();
        int instance = manager.getInstance(cameraLightEntity);
        if (instance == 0) return;
        float dx = (float)-cx, dy = (float)-cy, dz = (float)-cz;
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .0001f) return;
        manager.setDirection(instance, dx / length, dy / length, dz / length);
    }

    private void loadCarModel() throws Exception {
        materialProvider = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
        resourceLoader = new ResourceLoader(engine);
        asset = assetLoader.createAsset(readAsset(MODEL_FILE));
        if (asset == null) throw new IllegalStateException("Modele GLB non reconnu");
        resourceLoader.loadResources(asset);
        scene.addEntities(asset.getEntities());
        float[] c = asset.getBoundingBox().getCenter();
        float[] e = asset.getBoundingBox().getHalfExtent();
        System.arraycopy(c, 0, center, 0, 3);
        float longest = Math.max(e[0], Math.max(e[1], e[2]));
        modelScale = longest > .0001f ? 1.5f / longest : 1f;
        updateModel();
    }

    private ByteBuffer readAsset(String name) throws Exception {
        try (InputStream in = getContext().getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) != -1) out.write(chunk, 0, n);
            byte[] bytes = out.toByteArray();
            ByteBuffer result = ByteBuffer.allocateDirect(bytes.length)
                    .order(ByteOrder.nativeOrder());
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
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double cx = orbitRadius * Math.sin(yr) * Math.cos(pr);
        double cy = orbitRadius * Math.sin(pr);
        double cz = orbitRadius * Math.cos(yr) * Math.cos(pr);
        camera.lookAt(cx, cy, cz, 0, 0, 0, 0, 1, 0);
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        camera.setProjection(40.0, (double)width / height,
                .1, 100.0, Camera.Fov.VERTICAL);
        updateCameraLight(cx, cy, cz);
    }

    private void drawFrame(long frameTimeNanos) {
        if (engine == null || renderer == null || swapChain == null || view == null) return;
        if (renderer.beginFrame(swapChain, frameTimeNanos)) {
            renderer.render(view);
            renderer.endFrame();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format,
                                         int width, int height) {
        if (view != null) view.setViewport(new Viewport(0, 0,
                Math.max(1, width), Math.max(1, height)));
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
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                lastTouchTime = event.getEventTime();
                yawVelocity = pitchVelocity = 0f;
                pinching = false;
                touching = true;
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
                        orbitRadius = clamp(orbitRadius * previousPinchDistance / distance,
                                2.5f, 9f);
                        updateCamera();
                    }
                    previousPinchDistance = distance;
                    pinching = true;
                } else if (!pinching) {
                    float x = event.getX(), y = event.getY();
                    float dx = x - lastX, dy = y - lastY;
                    long currentTime = event.getEventTime();
                    float dt = Math.max(.008f, (currentTime - lastTouchTime) / 1000f);
                    yaw += dx * .5f;
                    pitch = clamp(pitch + dy * .3f, -55f, 65f);
                    yawVelocity = clamp(dx * .5f / dt, -240f, 240f);
                    pitchVelocity = clamp(dy * .3f / dt, -150f, 150f);
                    lastX = x;
                    lastY = y;
                    lastTouchTime = currentTime;
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
                touching = false;
                pinching = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                pinching = false;
                yawVelocity = pitchVelocity = 0f;
                return true;
            default:
                return true;
        }
    }
}
