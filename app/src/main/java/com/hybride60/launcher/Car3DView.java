
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

public class Car3DView extends SurfaceView
        implements SurfaceHolder.Callback {

    private static final String TAG = "HybridE60";

    // MODELE BMW CORRIGE
    private static final String MODEL_FILE =
            "bmw_e60_gris_corrige.glb";

    // FOND SHOWROOM ANTHRACITE
    private static final float BG_R = 0.055f;
    private static final float BG_G = 0.065f;
    private static final float BG_B = 0.080f;

    // ECLAIRAGE EQUILIBRE
    private static final float AMBIENT = 18000f;
    private static final float MAIN_LIGHT = 22000f;
    private static final float SIDE_LIGHT = 16000f;
    private static final float CAMERA_LIGHT = 13000f;

    private Engine engine;
    private Renderer renderer;
    private Scene scene;
    private View view;
    private Camera camera;
    private SwapChain swapChain;

    private IndirectLight indirectLight;

    private AssetLoader assetLoader;
    private ResourceLoader resourceLoader;
    private MaterialProvider materialProvider;
    private FilamentAsset asset;

    private int cameraEntity;
    private int cameraLightEntity;

    private final int[] lights = new int[6];

    // CAMERA ORBITALE
    private float yaw = -55f;
    private float pitch = 12f;
    private float orbitRadius = 3.8f;

    // CONTROLES TACTILES
    private float lastX;
    private float lastY;
    private long lastTouchTime;
    private float previousPinchDistance;

    private boolean touching = false;
    private boolean pinching = false;

    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;

    // NORMALISATION DU MODELE
    private float modelScale = 1f;
    private final float[] center = new float[3];

    private boolean running = false;
    private long lastFrameNanos = 0L;

    // ANIMATION
    private final Choreographer.FrameCallback frameCallback =
            new Choreographer.FrameCallback() {

        @Override
        public void doFrame(long frameTimeNanos) {

            if (!running) return;

            if (lastFrameNanos != 0L) {

                float dt = Math.min(
                        0.05f,
                        (frameTimeNanos - lastFrameNanos)
                                / 1_000_000_000f
                );

                if (!touching && !pinching &&
                        Math.abs(yawVelocity)
                                + Math.abs(pitchVelocity) > 0.02f) {

                    yaw += yawVelocity * dt;

                    pitch = clamp(
                            pitch + pitchVelocity * dt,
                            -55f,
                            65f
                    );

                    float damping =
                            (float) Math.exp(-5.5f * dt);

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

            if (running) {
                Choreographer.getInstance()
                        .postFrameCallback(this);
            }
        }
    };

    public Car3DView(Context context) {

        super(context);

        getHolder().setFormat(PixelFormat.OPAQUE);
        getHolder().addCallback(this);

        setFocusable(true);
    }

    // INITIALISATION SURFACE
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {

            if (engine == null) {

                initializeFilament();
                initializeEnvironment();
                initializeLights();
                loadCarModel();
            }

            if (swapChain == null) {

                swapChain = engine.createSwapChain(
                        holder.getSurface()
                );
            }

            updateCamera();

            running = true;
            lastFrameNanos = 0L;

            Choreographer.getInstance()
                    .removeFrameCallback(frameCallback);

            Choreographer.getInstance()
                    .postFrameCallback(frameCallback);

        } catch (Throwable error) {

            running = false;

            Log.e(TAG, "Erreur initialisation BMW", error);

            Toast.makeText(
                    getContext(),
                    "Erreur BMW 3D : " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // MOTEUR FILAMENT
    private void initializeFilament() {

        com.google.android.filament.Filament.init();
        com.google.android.filament.gltfio.Gltfio.init();

        engine = Engine.create();
        renderer = engine.createRenderer();

        Renderer.ClearOptions options =
                new Renderer.ClearOptions();

        options.clear = true;
        options.discard = true;

        options.clearColor = new float[]{
                BG_R,
                BG_G,
                BG_B,
                1f
        };

        renderer.setClearOptions(options);

        scene = engine.createScene();
        view = engine.createView();

        cameraEntity = EntityManager.get().create();
        camera = engine.createCamera(cameraEntity);

        view.setCamera(camera);
        view.setScene(scene);

        view.setBlendMode(View.BlendMode.OPAQUE);

        // Post-traitement
        view.setPostProcessingEnabled(true);

        // Exposition fixe
        camera.setExposure(
                16f,
                1f / 125f,
                100f
        );
    }

    // LUMIERE AMBIANTE DIFFUSE
    private void initializeEnvironment() {

        // 9 coefficients RGB = 27 valeurs.
        float[] sh = new float[27];

        // Premier coefficient : eclairage uniforme.
        // Facteur SH pour une radiance constante.
        sh[0] = 0.8f / 0.282095f;
        sh[1] = 0.82f / 0.282095f;
        sh[2] = 0.85f / 0.282095f;

        indirectLight = new IndirectLight.Builder()
                .irradiance(3, sh)
                .intensity(AMBIENT)
                .build(engine);

        scene.setIndirectLight(indirectLight);
    }

    // ECLAIRAGE SHOWROOM 360 DEGRES
    private void initializeLights() {

        // GAUCHE
        lights[0] = createDirectionalLight(
                MAIN_LIGHT,
                1f, 0.99f, 0.98f,
                -1f, -0.7f, 0f
        );

        // DROITE
        lights[1] = createDirectionalLight(
                MAIN_LIGHT,
                0.98f, 0.99f, 1f,
                1f, -0.7f, 0f
        );

        // TOIT
        lights[2] = createDirectionalLight(
                17000f,
                1f, 1f, 1f,
                0f, -1f, 0f
        );

        // AVANT
        lights[3] = createDirectionalLight(
                SIDE_LIGHT,
                1f, 1f, 1f,
                0f, -0.5f, -1f
        );

        // ARRIERE
        lights[4] = createDirectionalLight(
                SIDE_LIGHT,
                1f, 1f, 1f,
                0f, -0.5f, 1f
        );

        // OBLIQUE
        lights[5] = createDirectionalLight(
                9000f,
                1f, 1f, 1f,
                -0.7f, -0.5f, 0.7f
        );

        // LUMIERE CAMERA
        cameraLightEntity = createDirectionalLight(
                CAMERA_LIGHT,
                1f, 1f, 1f,
                0f, -0.4f, -1f
        );
    }

    private int createDirectionalLight(
            float intensity,
            float r,
            float g,
            float b,
            float dx,
            float dy,
            float dz) {

        int entity = EntityManager.get().create();

        new LightManager.Builder(
                LightManager.Type.DIRECTIONAL
        )
                .color(r, g, b)
                .intensity(intensity)
                .direction(dx, dy, dz)
                .castShadows(false)
                .build(engine, entity);

        scene.addEntity(entity);

        return entity;
    }

    // ECLAIRAGE QUI SUIT LA CAMERA
    private void updateCameraLight(
            double cx,
            double cy,
            double cz) {

        if (engine == null || cameraLightEntity == 0)
            return;

        LightManager manager =
                engine.getLightManager();

        int instance =
                manager.getInstance(cameraLightEntity);

        if (instance == 0) return;

        float dx = (float) -cx;
        float dy = (float) -cy;
        float dz = (float) -cz;

        float length = (float) Math.sqrt(
                dx * dx + dy * dy + dz * dz
        );

        if (length < 0.0001f) return;

        manager.setDirection(
                instance,
                dx / length,
                dy / length,
                dz / length
        );
    }

    // CHARGEMENT BMW GLB
    private void loadCarModel() throws Exception {

        materialProvider =
                new UbershaderProvider(engine);

        assetLoader = new AssetLoader(
                engine,
                materialProvider,
                EntityManager.get()
        );

        resourceLoader = new ResourceLoader(engine);

        asset = assetLoader.createAsset(
                readAsset(MODEL_FILE)
        );

        if (asset == null) {
            throw new IllegalStateException(
                    "Fichier GLB non reconnu"
            );
        }

        resourceLoader.loadResources(asset);

        scene.addEntities(asset.getEntities());

        float[] c = asset.getBoundingBox().getCenter();
        float[] e = asset.getBoundingBox().getHalfExtent();

        System.arraycopy(c, 0, center, 0, 3);

        float longest = Math.max(
                e[0],
                Math.max(e[1], e[2])
        );

        modelScale = longest > 0.0001f
                ? 1.5f / longest
                : 1f;

        updateModel();
    }

    // LECTURE ASSET
    private ByteBuffer readAsset(String name)
            throws Exception {

        try (
                InputStream in =
                        getContext().getAssets().open(name);

                ByteArrayOutputStream out =
                        new ByteArrayOutputStream()
        ) {

            byte[] buffer = new byte[16384];

            int count;

            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }

            byte[] bytes = out.toByteArray();

            ByteBuffer result =
                    ByteBuffer.allocateDirect(bytes.length)
                            .order(ByteOrder.nativeOrder());

            result.put(bytes);
            result.flip();

            return result;
        }
    }

    // POSITIONNEMENT DU MODELE
    private void updateModel() {

        if (asset == null || engine == null)
            return;

        float[] normalization = new float[16];

        Matrix.setIdentityM(normalization, 0);

        Matrix.scaleM(
                normalization,
                0,
                modelScale,
                modelScale,
                modelScale
        );

        Matrix.translateM(
                normalization,
                0,
                -center[0],
                -center[1],
                -center[2]
        );

        TransformManager manager =
                engine.getTransformManager();

        int instance =
                manager.getInstance(asset.getRoot());

        if (instance != 0) {

            manager.setTransform(
                    instance,
                    normalization
            );
        }
    }

    // CAMERA 360 DEGRES
    private void updateCamera() {

        if (camera == null) return;

        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);

        double cx = orbitRadius
                * Math.sin(yr)
                * Math.cos(pr);

        double cy = orbitRadius
                * Math.sin(pr);

        double cz = orbitRadius
                * Math.cos(yr)
                * Math.cos(pr);

        camera.lookAt(
                cx, cy, cz,
                0.0, 0.0, 0.0,
                0.0, 1.0, 0.0
        );

        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());

        camera.setProjection(
                40.0,
                (double) width / height,
                0.1,
                100.0,
                Camera.Fov.VERTICAL
        );

        updateCameraLight(cx, cy, cz);
    }

    // RENDU
    private void drawFrame(long frameTimeNanos) {

        if (engine == null ||
                renderer == null ||
                swapChain == null ||
                view == null) {
            return;
        }

        if (renderer.beginFrame(
                swapChain,
                frameTimeNanos
        )) {

            renderer.render(view);
            renderer.endFrame();
        }
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height) {

        if (view != null) {

            view.setViewport(
                    new Viewport(
                            0,
                            0,
                            Math.max(1, width),
                            Math.max(1, height)
                    )
            );
        }

        updateCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        running = false;

        Choreographer.getInstance()
                .removeFrameCallback(frameCallback);

        if (engine != null && swapChain != null) {

            engine.flushAndWait();

            engine.destroySwapChain(swapChain);

            swapChain = null;
        }
    }

    private static float clamp(
            float value,
            float min,
            float max) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private static float pointerDistance(
            MotionEvent event) {

        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);

        return (float) Math.sqrt(
                dx * dx + dy * dy
        );
    }

    // ROTATION ET ZOOM TACTILE
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                lastX = event.getX();
                lastY = event.getY();

                lastTouchTime = event.getEventTime();

                yawVelocity = 0f;
                pitchVelocity = 0f;

                pinching = false;
                touching = true;

                return true;

            case MotionEvent.ACTION_POINTER_DOWN:

                if (event.getPointerCount() >= 2) {

                    pinching = true;

                    previousPinchDistance =
                            pointerDistance(event);

                    yawVelocity = 0f;
                    pitchVelocity = 0f;
                }

                return true;

            case MotionEvent.ACTION_MOVE:

                if (event.getPointerCount() >= 2) {

                    float distance =
                            pointerDistance(event);

                    if (previousPinchDistance > 10f &&
                            distance > 10f) {

                        orbitRadius = clamp(
                                orbitRadius
                                        * previousPinchDistance
                                        / distance,
                                2.5f,
                                9f
                        );

                        updateCamera();
                    }

                    previousPinchDistance = distance;
                    pinching = true;

                } else if (!pinching) {

                    float x = event.getX();
                    float y = event.getY();

                    float dx = x - lastX;
                    float dy = y - lastY;

                    long currentTime =
                            event.getEventTime();

                    float dt = Math.max(
                            0.008f,
                            (currentTime - lastTouchTime)
                                    / 1000f
                    );

                    yaw += dx * 0.5f;

                    pitch = clamp(
                            pitch + dy * 0.3f,
                            -55f,
                            65f
                    );

                    yawVelocity = clamp(
                            dx * 0.5f / dt,
                            -240f,
                            240f
                    );

                    pitchVelocity = clamp(
                            dy * 0.3f / dt,
                            -150f,
                            150f
                    );

                    lastX = x;
                    lastY = y;

                    lastTouchTime = currentTime;

                    updateCamera();
                }

                return true;

            case MotionEvent.ACTION_POINTER_UP:

                pinching = false;

                yawVelocity = 0f;
                pitchVelocity = 0f;

                if (event.getPointerCount() >= 2) {

                    int remaining =
                            event.getActionIndex() == 0
                                    ? 1 : 0;

                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);

                    lastTouchTime =
                            event.getEventTime();
                }

                return true;

            case MotionEvent.ACTION_UP:

                touching = false;
                pinching = false;

                return true;

            case MotionEvent.ACTION_CANCEL:

                touching = false;
                pinching = false;

                yawVelocity = 0f;
                pitchVelocity = 0f;

                return true;

            default:
                return true;
        }
    }
}
