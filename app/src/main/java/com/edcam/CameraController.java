package com.edcam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All Camera2 state transitions run on the main looper. A generation invalidates stale callbacks.
 */
public final class CameraController {
  public interface Listener {
    void ready();

    void error(String message);

    void captured(byte[] jpeg, boolean mirrored);

    default void zoomChanged(float zoom, float min, float max) {}

    default void recordingStarted() {}

    default void recordingFinished(File video, String error) {}
  }

  private final Activity activity;
  private final TextureView texture;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private CameraDevice camera;
  private CameraCaptureSession session;
  private CaptureRequest.Builder preview;
  private ImageReader reader;
  private Surface previewSurface;
  private CameraCharacteristics characteristics;
  private boolean front, flash, opening, capturing, enabled;
  private int generation, captureSequence;
  private boolean waitingForFlash, sawPrecapture;
  private Size previewSize;
  private HandlerThread imageThread;
  private CameraCatalog catalog;
  private CameraCatalog.Lens lens;
  private float zoom = 1;
  private int exposure;
  private boolean videoMode, preparingVideo;
  private VideoRecorder recording;
  private final ExecutorService recordingWorker = Executors.newSingleThreadExecutor();

  public CameraController(
      Activity activity, TextureView texture, boolean front, Listener listener) {
    this.front = front;
    this.activity = activity;
    this.texture = texture;
    this.listener = listener;
    texture.setSurfaceTextureListener(
        new TextureView.SurfaceTextureListener() {
          public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
            open();
          }

          public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
            transform();
          }

          public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
            close();
            return true;
          }

          public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
  }

  public void start() {
    enabled = true;
    if (texture.isAvailable()) open();
  }

  public void stop() {
    enabled = false;
    close();
  }

  public void release() {
    stop();
    recordingWorker.shutdown();
  }

  private static Size chooseVideo(Size[] sizes) {
    if (sizes == null || sizes.length == 0)
      throw new IllegalArgumentException("Camera không hỗ trợ video.");
    Size best = null;
    for (Size size : sizes) {
      if (size.getWidth() > 1920 || size.getHeight() > 1080) continue;
      if (Math.abs(size.getWidth() / (float) size.getHeight() - 16f / 9) > .03f) continue;
      if (best == null || size.getWidth() > best.getWidth()) best = size;
    }
    return best == null ? choose(sizes, 1280 * 720) : best;
  }

  public void startVideo(VideoStore store, boolean audio) {
    if (!isReady() || recording != null) return;
    final int token = generation;
    final VideoRecorder recorder = new VideoRecorder(store);
    recording = recorder;
    preparingVideo = true;
    try {
      Size size =
          chooseVideo(
              characteristics
                  .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                  .getOutputSizes(android.media.MediaRecorder.class));
      Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      int orientation =
          ((sensor == null ? 90 : sensor) + (front ? rotationDegrees() : -rotationDegrees()) + 360)
              % 360;
      recordingWorker.execute(
          () -> {
            try {
              recorder.prepare(size, orientation, audio, () -> main.post(this::stopVideo));
              main.post(
                  () -> {
                    if (token != generation || !enabled || recording != recorder) return;
                    configureRecording(recorder, token);
                  });
            } catch (Exception e) {
              main.post(
                  () -> {
                    if (recording == recorder)
                      finishRecording(
                          true,
                          "Không khởi tạo được quay video. Kiểm tra dung lượng và quyền micro.");
                  });
            }
          });
    } catch (RuntimeException e) {
      finishRecording(true, "Camera không hỗ trợ quay video với cấu hình này.");
    }
  }

  @SuppressWarnings("deprecation")
  private void configureRecording(VideoRecorder recorder, int token) {
    try {
      if (session != null) session.close();
      session = null;
      Surface output = recorder.surface();
      CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
      request.addTarget(previewSurface);
      request.addTarget(output);
      autoFocusMode(request, true);
      applyZoom(request);
      camera.createCaptureSession(
          Arrays.asList(previewSurface, output),
          new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession configured) {
              if (token != generation || recording != recorder) {
                configured.close();
                return;
              }
              session = configured;
              preview = request;
              try {
                session.setRepeatingRequest(request.build(), null, main);
                recorder.start();
                preparingVideo = false;
                listener.recordingStarted();
              } catch (CameraAccessException | RuntimeException e) {
                finishRecording(true, "Không bắt đầu được video. Hãy thử lại.");
              }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession failed) {
              if (token == generation && recording == recorder)
                finishRecording(true, "Camera không hỗ trợ cấu hình quay này.");
            }
          },
          main);
    } catch (CameraAccessException | RuntimeException e) {
      finishRecording(true, "Không mở được phiên quay video.");
    }
  }

  public void pauseVideo(boolean paused) {
    if (recording == null || preparingVideo) return;
    try {
      recording.pause(paused);
    } catch (RuntimeException e) {
      finishRecording(true, "Không thể tạm dừng video trên thiết bị này.");
    }
  }

  public void stopVideo() {
    finishRecording(true, null);
  }

  private void finishRecording(boolean restore, String reason) {
    VideoRecorder recorder = recording;
    if (recorder == null) return;
    recording = null;
    preparingVideo = false;
    final int token = generation;
    if (session != null) {
      session.close();
      session = null;
    }
    recordingWorker.execute(
        () -> {
          File saved = null;
          String error = reason;
          try {
            saved = recorder.finish();
          } catch (Exception e) {
            error = "Video chưa lưu được. Có thể lượt quay quá ngắn hoặc bộ nhớ đã đầy.";
          }
          final File result = saved;
          final String problem = error;
          main.post(
              () -> {
                if (restore && token == generation && enabled && camera != null)
                  createSession(token);
                listener.recordingFinished(result, problem);
              });
        });
  }

  public boolean isReady() {
    return session != null && !capturing && !preparingVideo;
  }

  public void flip() {
    front = !front;
    catalog = null;
    zoom = 1;
    close();
    if (enabled && texture.isAvailable()) open();
  }

  public boolean hasFlash() {
    return characteristics != null
        && Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
  }

  public boolean setFlash(boolean requested) {
    flash = requested && hasFlash();
    return flash;
  }

  public void color(Presets.Style style, float strength) {
    Paint p = new Paint();
    p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(Presets.matrix(style, strength))));
    texture.setLayerPaint(p);
    texture.invalidate();
  }

  public void setVideoMode(boolean value) {
    videoMode = value;
  }

  public boolean isRecording() {
    return recording != null;
  }

  public float zoom() {
    return zoom;
  }

  public float minZoom() {
    return recording != null && lens != null ? lens.minZoom() : catalog == null ? 1 : catalog.min;
  }

  public float maxZoom() {
    return recording != null && lens != null ? lens.maxZoom() : catalog == null ? 1 : catalog.max;
  }

  public int minExposure() {
    android.util.Range<Integer> range =
        characteristics == null
            ? null
            : characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    return range == null ? 0 : range.getLower();
  }

  public int maxExposure() {
    android.util.Range<Integer> range =
        characteristics == null
            ? null
            : characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    return range == null ? 0 : range.getUpper();
  }

  public int exposure() {
    return exposure;
  }

  public void setExposure(int requested) {
    exposure = Math.max(minExposure(), Math.min(maxExposure(), requested));
    if (session == null || preview == null) return;
    try {
      preview.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure);
      session.setRepeatingRequest(preview.build(), null, main);
    } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
      listener.error("Camera không đổi được độ sáng này.");
    }
  }

  public void setZoom(float requested) {
    if (catalog == null || lens == null) return;
    float next = ZoomMath.clamp(requested, catalog.min, catalog.max);
    CameraCatalog.Lens selected = catalog.select(next);
    if (recording != null && selected != lens) {
      next = ZoomMath.clamp(next, lens.minZoom(), lens.maxZoom());
      selected = lens;
    }
    zoom = next;
    listener.zoomChanged(zoom, minZoom(), maxZoom());
    if (selected != lens) {
      close();
      if (enabled) open();
      return;
    }
    if (session == null || preview == null) return;
    try {
      applyZoom(preview);
      session.setRepeatingRequest(preview.build(), null, main);
    } catch (CameraAccessException | IllegalArgumentException | IllegalStateException e) {
      listener.error("Camera không đổi được mức zoom này. Hãy thử mức khác.");
    }
  }

  private void applyZoom(CaptureRequest.Builder request) {
    if (lens == null) return;
    exposure = Math.max(minExposure(), Math.min(maxExposure(), exposure));
    request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure);
    float local = ZoomMath.clamp(zoom / lens.base, lens.min, lens.max);
    if (Build.VERSION.SDK_INT >= 30 && lens.ratioControl) {
      request.set(CaptureRequest.CONTROL_ZOOM_RATIO, local);
    } else {
      Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      if (sensor == null) return;
      int width = Math.max(1, Math.round(sensor.width() / local));
      int height = Math.max(1, Math.round(sensor.height() / local));
      int left = sensor.left + (sensor.width() - width) / 2;
      int top = sensor.top + (sensor.height() - height) / 2;
      request.set(
          CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + width, top + height));
    }
  }

  private void autoFocusMode(CaptureRequest.Builder request, boolean video) {
    int desired =
        video
            ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    if (modes != null)
      for (int mode : modes)
        if (mode == desired) {
          request.set(CaptureRequest.CONTROL_AF_MODE, desired);
          return;
        }
  }

  private void open() {
    if (!enabled
        || opening
        || camera != null
        || activity.checkSelfPermission(Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return;
    opening = true;
    final int token = ++generation;
    try {
      CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
      if (catalog == null) catalog = new CameraCatalog(manager, front);
      lens = catalog.select(zoom);
      if (lens == null) {
        opening = false;
        listener.error(front ? "Thiết bị không có camera trước." : "Không tìm thấy camera sau.");
        return;
      }
      String selected = lens.id;
      characteristics = lens.info;
      zoom = ZoomMath.clamp(zoom, lens.minZoom(), lens.maxZoom());
      listener.zoomChanged(zoom, minZoom(), maxZoom());
      StreamConfigurationMap map =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map == null) throw new IllegalStateException("Không có cấu hình camera.");
      Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
      previewSize = videoMode ? chooseVideo(sizes) : choose(sizes, 1920 * 1080);
      Size jpeg = choose(map.getOutputSizes(ImageFormat.JPEG), 12_500_000);
      reader = ImageReader.newInstance(jpeg.getWidth(), jpeg.getHeight(), ImageFormat.JPEG, 2);
      imageThread = new HandlerThread("EdCamImageReader");
      imageThread.start();
      reader.setOnImageAvailableListener(
          source -> {
            try (Image image = source.acquireLatestImage()) {
              if (image == null) return;
              ByteBuffer buffer = image.getPlanes()[0].getBuffer();
              byte[] bytes = new byte[buffer.remaining()];
              buffer.get(bytes);
              main.post(
                  () -> {
                    if (token == generation) {
                      capturing = false;
                      waitingForFlash = false;
                      restorePreview();
                      listener.captured(bytes, front);
                    }
                  });
            } catch (RuntimeException e) {
              main.post(
                  () -> {
                    if (token == generation) {
                      capturing = false;
                      listener.error("Không đọc được ảnh. Hãy chụp lại.");
                    }
                  });
            }
          },
          new Handler(imageThread.getLooper()));
      texture
          .getSurfaceTexture()
          .setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
      transform();
      manager.openCamera(
          selected,
          new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice device) {
              opening = false;
              if (token != generation || !enabled) {
                device.close();
                return;
              }
              camera = device;
              createSession(token);
            }

            public void onDisconnected(CameraDevice device) {
              device.close();
              if (token == generation) {
                close();
                listener.error("Camera đã ngắt kết nối. Chạm để thử lại.");
              }
            }

            public void onError(CameraDevice device, int error) {
              device.close();
              if (token == generation) {
                close();
                listener.error("Camera đang bận hoặc không khả dụng. Chạm để thử lại.");
              }
            }
          },
          main);
    } catch (CameraAccessException
        | SecurityException
        | IllegalStateException
        | IllegalArgumentException e) {
      close();
      listener.error("Không mở được camera. Kiểm tra quyền camera và thử lại.");
    }
  }

  private static Size choose(Size[] sizes, int cap) {
    if (sizes == null || sizes.length == 0)
      throw new IllegalArgumentException("Camera không có kích thước phù hợp.");
    Size best = null;
    double score = Double.MAX_VALUE;
    for (Size size : sizes) {
      long area = (long) size.getWidth() * size.getHeight();
      if (area > cap) continue;
      double current =
          Math.abs(size.getWidth() / (double) size.getHeight() - 4.0 / 3) * 20
              + Math.abs(Math.log(cap / (double) area));
      if (current < score) {
        score = current;
        best = size;
      }
    }
    if (best != null) return best;
    return Collections.min(
        Arrays.asList(sizes), Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()));
  }

  @SuppressWarnings("deprecation")
  private void createSession(int token) {
    try {
      if (previewSurface != null) previewSurface.release();
      previewSurface = new Surface(texture.getSurfaceTexture());
      preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      preview.addTarget(previewSurface);
      autoFocusMode(preview, videoMode);
      applyZoom(preview);
      camera.createCaptureSession(
          Arrays.asList(previewSurface, reader.getSurface()),
          new CameraCaptureSession.StateCallback() {
            public void onConfigured(CameraCaptureSession configured) {
              if (token != generation || camera == null) {
                configured.close();
                return;
              }
              session = configured;
              try {
                session.setRepeatingRequest(preview.build(), null, main);
                listener.ready();
              } catch (CameraAccessException | IllegalStateException e) {
                listener.error("Không hiển thị được camera. Hãy mở lại màn hình chụp.");
              }
            }

            public void onConfigureFailed(CameraCaptureSession failed) {
              if (token == generation) {
                close();
                listener.error("Camera không hỗ trợ cấu hình này.");
              }
            }
          },
          main);
    } catch (CameraAccessException | IllegalStateException e) {
      close();
      listener.error("Không khởi tạo được camera.");
    }
  }

  public void capture() {
    if (!isReady()) {
      listener.error("Camera chưa sẵn sàng.");
      return;
    }
    capturing = true;
    final int token = generation, sequence = ++captureSequence;
    main.postDelayed(
        () -> {
          if (token == generation && sequence == captureSequence && capturing) {
            capturing = false;
            waitingForFlash = false;
            restorePreview();
            listener.error("Camera phản hồi quá lâu. Hãy chụp lại.");
          }
        },
        8000);
    if (!flash || !hasFlash()) {
      captureStill(token);
      return;
    }
    // Some phones require the AE precapture cycle before a still request can fire the strobe.
    waitingForFlash = true;
    sawPrecapture = false;
    try {
      preview.set(CaptureRequest.CONTROL_AE_MODE, flashAeMode());
      preview.set(
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      CameraCaptureSession.CaptureCallback exposure =
          new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(
                CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
              if (token != generation || sequence != captureSequence || !waitingForFlash) return;
              Integer state = result.get(CaptureResult.CONTROL_AE_STATE);
              if (state != null && state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
                sawPrecapture = true;
              if (state == null
                  || (sawPrecapture && state != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)) {
                waitingForFlash = false;
                captureStill(token);
              }
            }
          };
      session.capture(preview.build(), exposure, main);
      preview.set(
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
      session.setRepeatingRequest(preview.build(), exposure, main);
      // Bounded fallback for HALs that never report PRECAPTURE (including some legacy cameras).
      main.postDelayed(
          () -> {
            if (token == generation && sequence == captureSequence && waitingForFlash) {
              waitingForFlash = false;
              captureStill(token);
            }
          },
          1800);
    } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
      waitingForFlash = false;
      captureStill(token);
    }
  }

  private int flashAeMode() {
    int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
    if (modes != null)
      for (int mode : modes)
        if (mode == CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) return mode;
    return CaptureRequest.CONTROL_AE_MODE_ON;
  }

  private void restorePreview() {
    if (session == null || preview == null) return;
    try {
      preview.set(
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
      preview.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
      preview.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
      session.setRepeatingRequest(preview.build(), null, main);
    } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
      android.util.Log.w("EdCamCamera", "Preview restore failed", e);
    }
  }

  private void captureStill(final int token) {
    if (token != generation || camera == null || session == null || !capturing) return;
    try {
      CaptureRequest.Builder request =
          camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      request.addTarget(reader.getSurface());
      autoFocusMode(request, false);
      applyZoom(request);
      request.set(
          CaptureRequest.CONTROL_AE_MODE,
          flash && hasFlash() ? flashAeMode() : CaptureRequest.CONTROL_AE_MODE_ON);
      request.set(
          CaptureRequest.FLASH_MODE,
          flash && hasFlash() ? CaptureRequest.FLASH_MODE_SINGLE : CaptureRequest.FLASH_MODE_OFF);
      Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      int degrees = rotationDegrees();
      request.set(
          CaptureRequest.JPEG_ORIENTATION,
          ((sensor == null ? 90 : sensor) + (front ? degrees : -degrees) + 360) % 360);
      session.capture(
          request.build(),
          new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureFailed(
                CameraCaptureSession s, CaptureRequest r, CaptureFailure f) {
              if (token == generation) {
                capturing = false;
                restorePreview();
                listener.error("Chụp ảnh thất bại. Hãy thử lại.");
              }
            }
          },
          main);
    } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
      capturing = false;
      restorePreview();
      listener.error("Không chụp được ảnh. Hãy mở lại camera.");
    }
  }

  public void focus(float x, float y) {
    if (session == null || preview == null || preparingVideo) return;
    try {
      Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      Integer regions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
      if (sensor == null || regions == null || regions == 0) return;
      int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
      boolean canFocus = false;
      if (modes != null)
        for (int mode : modes) if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) canFocus = true;
      if (!canFocus) return;
      Matrix inverse = new Matrix();
      float[] point = {x * texture.getWidth(), y * texture.getHeight()};
      if (texture.getTransform(new Matrix()).invert(inverse)) inverse.mapPoints(point);
      x = Math.max(0, Math.min(1, point[0] / Math.max(1, texture.getWidth())));
      y = Math.max(0, Math.min(1, point[1] / Math.max(1, texture.getHeight())));
      if (front) x = 1 - x;
      Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      int rotation = ((orientation == null ? 90 : orientation) - rotationDegrees() + 360) % 360;
      float sx = x, sy = y;
      if (rotation == 90) {
        sx = y;
        sy = 1 - x;
      } else if (rotation == 180) {
        sx = 1 - x;
        sy = 1 - y;
      } else if (rotation == 270) {
        sx = 1 - y;
        sy = x;
      }
      Rect region = preview.get(CaptureRequest.SCALER_CROP_REGION);
      if (region == null || (Build.VERSION.SDK_INT >= 30 && lens != null && lens.ratioControl))
        region = sensor;
      int cx = Math.round(region.left + sx * region.width()),
          cy = Math.round(region.top + sy * region.height());
      int side = Math.min(sensor.width(), sensor.height()) / 12;
      int left = Math.max(sensor.left, Math.min(sensor.right - side, cx - side / 2)),
          top = Math.max(sensor.top, Math.min(sensor.bottom - side, cy - side / 2));
      preview.set(
          CaptureRequest.CONTROL_AF_REGIONS,
          new MeteringRectangle[] {
            new MeteringRectangle(
                new Rect(left, top, left + side, top + side), MeteringRectangle.METERING_WEIGHT_MAX)
          });
      preview.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
      preview.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
      session.capture(preview.build(), null, main);
      preview.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
      session.setRepeatingRequest(preview.build(), null, main);
      final int token = generation;
      main.postDelayed(
          () -> {
            if (token != generation || session == null) return;
            try {
              autoFocusMode(preview, recording != null || videoMode);
              session.setRepeatingRequest(preview.build(), null, main);
            } catch (CameraAccessException | IllegalStateException ignored) {
              /* Session may close while regaining continuous focus. */
            }
          },
          2500);
    } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
      listener.error("Không thể lấy nét tại điểm này.");
    }
  }

  private int rotationDegrees() {
    switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_90:
        return 90;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_270:
        return 270;
      default:
        return 0;
    }
  }

  private void transform() {
    if (previewSize == null || texture.getWidth() == 0 || texture.getHeight() == 0) return;
    float width = texture.getWidth(), height = texture.getHeight();
    Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    boolean swapped = sensor != null && sensor % 180 != 0;
    float natural =
        swapped
            ? previewSize.getHeight() / (float) previewSize.getWidth()
            : previewSize.getWidth() / (float) previewSize.getHeight();
    float target = width / height;
    Matrix matrix = new Matrix();
    matrix.setScale(
        Math.max(1, natural / target), Math.max(1, target / natural), width / 2, height / 2);
    texture.setTransform(matrix);
  }

  private void close() {
    finishRecording(false, null);
    generation++;
    opening = false;
    capturing = false;
    waitingForFlash = false;
    captureSequence++;
    if (session != null) {
      session.close();
      session = null;
    }
    if (camera != null) {
      camera.close();
      camera = null;
    }
    if (reader != null) {
      reader.close();
      reader = null;
    }
    if (imageThread != null) {
      imageThread.quitSafely();
      imageThread = null;
    }
    if (previewSurface != null) {
      previewSurface.release();
      previewSurface = null;
    }
    preview = null;
  }
}
