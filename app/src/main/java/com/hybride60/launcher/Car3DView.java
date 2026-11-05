
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

import com.google.android.filament.utils.KTX1Loader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Car3DView extends SurfaceView
        implements SurfaceHolder.Callback {

    private static final String TAG = "HybridE60";

    private static final String MODEL_FILE =
            "bmw_m5_e60_gris_test.glb";

    private static final String IBL_FILE =
            "RNF_default_env_ibl.ktx";

    // Intensité de l'éclairage d'environnement.
    private static final float IBL_INTENSITY = 30000f;

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
    private final int[] lightEntities = new int[3];

    private float yaw = -55f;
    private float pitch = 12f;
    private float orbitRadius = 3.8f;

    private float lastX;
    private float lastY;
    private long lastTouchTime;

    private float previousPinchDistance;
    private boolean pinching = false;

    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;

    private float modelScale = 1f;
    private final float[] center = new float[3];

    private boolean running = false;
    private long lastFrameNanos = 0L;

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

                if (!pinching &&
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

                } else {

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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {

            if (engine == null) {

                initializeFilament();

                initializeIBL();

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

            Log.e(TAG, "Erreur Filament", error);

            Toast.makeText(
                    getContext(),
                    "Erreur 3D : " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // Initialisation du moteur 3D.
    private void initializeFilament() {

        com.google.android.filament.Filament.init();
        com.google.android.filament.gltfio.Gltfio.init();

        engine = Engine.create();

        renderer = engine.createRenderer();

        Renderer.ClearOptions clearOptions =
                new Renderer.ClearOptions();

        clearOptions.clear = true;
        clearOptions.discard = true;

        // Fond noir.
        clearOptions.clearColor =
                new float[]{0f, 0f, 0f, 1f};

        renderer.setClearOptions(clearOptions);

        scene = engine.createScene();
        view = engine.createView();

        cameraEntity = EntityManager.get().create();

        camera = engine.createCamera(cameraEntity);

        view.setCamera(camera);
        view.setScene(scene);

        view.setBlendMode(View.BlendMode.OPAQUE);

        // On conserve ton rendu stable.
        view.setPostProcessingEnabled(false);
    }

    // NOUVEAU : éclairage indirect IBL.
    private void initializeIBL() {

        try {

            ByteBuffer iblBuffer =
                    readAsset(IBL_FILE);

            KTX1Loader.Options options =
                    new KTX1Loader.Options();

            indirectLight =
                    KTX1Loader.INSTANCE.createIndirectLight(
                            engine,
                            iblBuffer,
                            options
                    );

            if (indirectLight == null) {
                throw new IllegalStateException(
                        "Création de l'IBL impossible"
                );
            }

            indirectLight.setIntensity(IBL_INTENSITY);

            scene.setIndirectLight(indirectLight);

            // Aucun skybox :
            // le fond reste entièrement noir.

            Log.i(TAG, "Éclairage IBL activé");

        } catch (Throwable error) {

            Log.e(
                    TAG,
                    "Impossible de charger l'IBL",
                    error
            );

            // Le modèle peut continuer à fonctionner
            // avec les lumières classiques.
            indirectLight = null;

            Toast.makeText(
                    getContext(),
                    "IBL non chargé : vérifie le fichier KTX",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // Éclairage direct complémentaire.
    private void initializeLights() {

        // Lumière principale du dessus.
        addDirectionalLight(
                0,
                35000f,
                1.0f, 0.98f, 0.96f,
                -0.4f, -1.0f, -0.4f
        );

        // Lumière douce côté gauche.
        addDirectionalLight(
                1,
                12000f,
                1.0f, 1.0f, 1.0f,
                1.0f, -0.4f, 0.0f
        );

        // Lumière douce côté droit.
        addDirectionalLight(
                2,
                12000f,
                0.96f, 0.98f, 1.0f,
                -1.0f, -0.4f, 0.0f
        );
    }

    private void addDirectionalLight(
            int index,
            float intensity,
            float red,
            float green,
            float blue,
            float directionX,
            float directionY,
            float directionZ) {

        int entity = EntityManager.get().create();

        lightEntities[index] = entity;

        new LightManager.Builder(
                LightManager.Type.DIRECTIONAL
        )
                .color(red, green, blue)
                .intensity(intensity)
                .direction(
                        directionX,
                        directionY,
                        directionZ
                )
                .castShadows(false)
                .build(engine, entity);

        scene.addEntity(entity);
    }

    // Chargement de la BMW.
    private void loadCarModel() throws Exception {

        materialProvider = new UbershaderProvider(engine);

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
                    "Modèle GLB non reconnu"
            );
        }

        resourceLoader.loadResources(asset);

        scene.addEntities(asset.getEntities());

        float[] c =
                asset.getBoundingBox().getCenter();

        float[] e =
                asset.getBoundingBox().getHalfExtent();

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

    // Lecture des fichiers dans assets.
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

    // Centrage du modèle.
    private void updateModel() {

        if (asset == null || engine == null) return;

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

    // Caméra orbitale.
    private void updateCamera() {

        if (camera == null) return;

        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);

        double cx =
                orbitRadius * Math.sin(yr) * Math.cos(pr);

        double cy =
                orbitRadius * Math.sin(pr);

        double cz =
                orbitRadius * Math.cos(yr) * Math.cos(pr);

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
    }

    // Rendu.
    private void drawFrame(long frameTimeNanos) {

        if (engine == null ||
                renderer == null ||
                swapChain == null ||
                view == null) {

            return;
        }

        if (renderer.beginFrame(
                swapChain,
                frameTimeNanos)) {

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

        return Math.max(min, Math.min(max, value));
    }

    private static float pointerDistance(
            MotionEvent event) {

        float dx =
                event.getX(0) - event.getX(1);

        float dy =
                event.getY(0) - event.getY(1);

        return (float) Math.sqrt(
                dx * dx + dy * dy
        );
    }

    // Rotation tactile et zoom.
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

                    if (previousPinchDistance > 10f
                            && distance > 10f) {

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

                    lastTouchTime = event.getEventTime();
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
