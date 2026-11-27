package com.edcam;

import static android.opengl.GLES20.*;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.*;
import android.util.Size;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.FutureTask;

/** Draw camera frames directly into the encoder with the same RGB matrix as the viewfinder. */
final class VideoColorSurface {
  private final HandlerThread thread = new HandlerThread("EdCam video color");
  private final Handler handler;
  private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
  private EGLContext context = EGL14.EGL_NO_CONTEXT;
  private EGLSurface window = EGL14.EGL_NO_SURFACE;
  private SurfaceTexture texture;
  private Surface input;
  private final float[] transform = new float[16];
  private int transformLocation;

  Surface input() {
    return input;
  }

  VideoColorSurface(Surface output, Size size, float[] color) throws Exception {
    thread.start();
    handler = new Handler(thread.getLooper());
    FutureTask<Void> setup =
        new FutureTask<>(
            () -> {
              init(output, size, color);
              return null;
            });
    handler.post(setup);
    try {
      setup.get();
    } catch (Exception e) {
      close();
      throw e;
    }
  }

  private static int shader(int type, String text) {
    int id = glCreateShader(type);
    glShaderSource(id, text);
    glCompileShader(id);
    int[] status = new int[1];
    glGetShaderiv(id, GL_COMPILE_STATUS, status, 0);
    if (status[0] == 0) throw new IllegalStateException(glGetShaderInfoLog(id));
    return id;
  }

  private void init(Surface output, Size size, float[] color) {
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    int[] n = new int[1];
    EGLConfig[] config = new EGLConfig[1];
    int[] configAttrs = {
      EGL14.EGL_RENDERABLE_TYPE,
      EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_SURFACE_TYPE,
      EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_RED_SIZE,
      8,
      EGL14.EGL_GREEN_SIZE,
      8,
      EGL14.EGL_BLUE_SIZE,
      8,
      0x3142,
      1,
      EGL14.EGL_NONE
    };
    if (!EGL14.eglInitialize(display, n, 0, n, 0)
        || !EGL14.eglChooseConfig(display, configAttrs, 0, config, 0, 1, n, 0)
        || n[0] == 0) throw new IllegalStateException("Encoder EGL config");
    context =
        EGL14.eglCreateContext(
            display,
            config[0],
            EGL14.EGL_NO_CONTEXT,
            new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE},
            0);
    window =
        EGL14.eglCreateWindowSurface(display, config[0], output, new int[] {EGL14.EGL_NONE}, 0);
    if (!EGL14.eglMakeCurrent(display, window, window, context))
      throw new IllegalStateException("Encoder EGL surface");
    int program = glCreateProgram();
    glAttachShader(
        program,
        shader(
            GL_VERTEX_SHADER,
            "attribute vec2 pos;uniform mat4 uv;varying vec2 tc;void"
                + " main(){gl_Position=vec4(pos,0.,1.);tc=(uv*vec4(pos*.5+.5,0.,1.)).xy;}"));
    glAttachShader(
        program,
        shader(
            GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;uniform samplerExternalOES frame;uniform mat3"
                + " rgb;uniform vec3 bias;varying vec2 tc;void"
                + " main(){gl_FragColor=vec4(clamp(rgb*texture2D(frame,tc).rgb+bias,0.,1.),1.);}"));
    glLinkProgram(program);
    glGetProgramiv(program, GL_LINK_STATUS, n, 0);
    if (n[0] == 0) throw new IllegalStateException(glGetProgramInfoLog(program));
    glUseProgram(program);
    float[] vertices = {-1, -1, 1, -1, -1, 1, 1, 1};
    FloatBuffer coords =
        ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    coords.put(vertices).position(0);
    int pos = glGetAttribLocation(program, "pos");
    glVertexAttribPointer(pos, 2, GL_FLOAT, false, 0, coords);
    glEnableVertexAttribArray(pos);
    // Client-side attribute data must stay reachable for subsequent draws.
    vertexData = coords;
    glUniformMatrix3fv(
        glGetUniformLocation(program, "rgb"),
        1,
        false,
        new float[] {
          color[0], color[5], color[10], color[1], color[6], color[11], color[2], color[7],
          color[12]
        },
        0);
    glUniform3f(
        glGetUniformLocation(program, "bias"), color[4] / 255, color[9] / 255, color[14] / 255);
    transformLocation = glGetUniformLocation(program, "uv");
    glGenTextures(1, n, 0);
    int target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    glBindTexture(target, n[0]);
    glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glViewport(0, 0, size.getWidth(), size.getHeight());
    texture = new SurfaceTexture(n[0]);
    texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
    input = new Surface(texture);
    texture.setOnFrameAvailableListener(
        t -> {
          if (t != texture) return;
          t.updateTexImage();
          t.getTransformMatrix(transform);
          glUniformMatrix4fv(transformLocation, 1, false, transform, 0);
          glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
          EGLExt.eglPresentationTimeANDROID(display, window, t.getTimestamp());
          EGL14.eglSwapBuffers(display, window);
        },
        handler);
  }

  private FloatBuffer vertexData;

  void close() {
    FutureTask<Void> cleanup =
        new FutureTask<>(
            () -> {
              if (texture != null) texture.setOnFrameAvailableListener(null);
              if (input != null) input.release();
              if (texture != null) {
                texture.release();
                texture = null;
              }
              if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(display);
              }
              return null;
            });
    handler.post(cleanup);
    try {
      cleanup.get();
    } catch (Exception e) {
      android.util.Log.w("EdCam", "Encoder cleanup", e);
    } finally {
      thread.quitSafely();
    }
  }
}
