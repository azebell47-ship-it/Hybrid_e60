
package com.hybride60.launcher;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Car3DView extends GLSurfaceView {

    private final CarRenderer renderer;
    private float lastX;
    private float lastY;

    public Car3DView(Context context) {
        super(context);

        setEGLContextClientVersion(2);

        renderer = new CarRenderer();
        setRenderer(renderer);

        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getActionMasked() ==
                MotionEvent.ACTION_MOVE) {

            renderer.angleY += (x - lastX) * 0.5f;
            renderer.angleX += (y - lastY) * 0.3f;

            renderer.angleX = Math.max(
                -45, Math.min(45, renderer.angleX)
            );

            requestRender();
        }

        lastX = x;
        lastY = y;

        return true;
    }

    private static class CarRenderer
            implements GLSurfaceView.Renderer {

        volatile float angleY = -30f;
        volatile float angleX = 15f;

        private FloatBuffer vertices;
        private int program;
        private int positionHandle;
        private int matrixHandle;

        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] temp = new float[16];
        private final float[] mvp = new float[16];

        private final float[] cube = {
            -1,-1, 1,  1,-1, 1,  1, 1, 1,
            -1,-1, 1,  1, 1, 1, -1, 1, 1,

             1,-1,-1, -1,-1,-1, -1, 1,-1,
             1,-1,-1, -1, 1,-1,  1, 1,-1,

            -1,-1,-1, -1,-1, 1, -1, 1, 1,
            -1,-1,-1, -1, 1, 1, -1, 1,-1,

             1,-1, 1,  1,-1,-1,  1, 1,-1,
             1,-1, 1,  1, 1,-1,  1, 1, 1,

            -1, 1, 1,  1, 1, 1,  1, 1,-1,
            -1, 1, 1,  1, 1,-1, -1, 1,-1,

            -1,-1,-1,  1,-1,-1,  1,-1, 1,
            -1,-1,-1,  1,-1, 1, -1,-1, 1
        };

        @Override
        public void onSurfaceCreated(
                javax.microedition.khronos.opengles.GL10 gl,
                javax.microedition.khronos.egl.EGLConfig config) {

            GLES20.glClearColor(0.02f, 0.04f, 0.08f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            ByteBuffer buffer = ByteBuffer.allocateDirect(
                cube.length * 4
            );
            buffer.order(ByteOrder.nativeOrder());

            vertices = buffer.asFloatBuffer();
            vertices.put(cube);
            vertices.position(0);

            String vertexShader =
                "uniform mat4 uMVP;" +
                "attribute vec4 aPosition;" +
                "void main() {" +
                "gl_Position = uMVP * aPosition;" +
                "}";

            String fragmentShader =
                "precision mediump float;" +
                "void main() {" +
                "gl_FragColor = vec4(0.15,0.55,0.95,1.0);" +
                "}";

            int vs = compile(
                GLES20.GL_VERTEX_SHADER, vertexShader
            );

            int fs = compile(
                GLES20.GL_FRAGMENT_SHADER, fragmentShader
            );

            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);

            positionHandle = GLES20.glGetAttribLocation(
                program, "aPosition"
            );

            matrixHandle = GLES20.glGetUniformLocation(
                program, "uMVP"
            );
        }

        private int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            return shader;
        }

        @Override
        public void onSurfaceChanged(
                javax.microedition.khronos.opengles.GL10 gl,
                int width, int height) {

            GLES20.glViewport(0, 0, width, height);

            float ratio = (float) width /
                Math.max(1, height);

            Matrix.perspectiveM(
                projection, 0, 45f, ratio, 1f, 100f
            );
        }

        @Override
        public void onDrawFrame(
                javax.microedition.khronos.opengles.GL10 gl) {

            GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
            );

            Matrix.setLookAtM(
                view, 0,
                0, 0, 6,
                0, 0, 0,
                0, 1, 0
            );

            Matrix.setIdentityM(model, 0);

            Matrix.rotateM(
                model, 0, angleX, 1, 0, 0
            );

            Matrix.rotateM(
                model, 0, angleY, 0, 1, 0
            );

            Matrix.multiplyMM(
                temp, 0, view, 0, model, 0
            );

            Matrix.multiplyMM(
                mvp, 0, projection, 0, temp, 0
            );

            GLES20.glUseProgram(program);

            GLES20.glEnableVertexAttribArray(
                positionHandle
            );

            GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                vertices
            );

            GLES20.glUniformMatrix4fv(
                matrixHandle, 1, false, mvp, 0
            );

            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES, 0, 36
            );

            GLES20.glDisableVertexAttribArray(
                positionHandle
            );
        }
    }
}
