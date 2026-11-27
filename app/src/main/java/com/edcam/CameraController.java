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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * All Camera2 state transitions run on the main looper. A generation invalidates stale callbacks.
 */
public final class CameraController {
  public interface Listener {
    void ready();

    void error(String message);

    void captured(byte[] jpeg, boolean mirrored);
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
  private int generation;
  private Size previewSize;
  private HandlerThread imageThread;

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

  public boolean isReady() {
    return session != null && !capturing;
  }

  public void flip() {
    front = !front;
    close();
    if (enabled && texture.isAvailable()) open();
  }

  public boolean setFlash(boolean requested) {
    boolean available =
        characteristics != null
            && Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
    flash = requested && available;
    return flash;
  }

  public void color(Presets.Style style, float strength) {
    Paint p = new Paint();
    p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(Presets.matrix(style, strength))));
    texture.setLayerPaint(p);
    texture.invalidate();
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
      String selected = null;
      for (String id : manager.getCameraIdList()) {
        CameraCharacteristics info = manager.getCameraCharacteristics(id);
        Integer facing = info.get(CameraCharacteristics.LENS_FACING);
        if (facing != null
            && facing
                == (front
                    ? CameraCharacteristics.LENS_FACING_FRONT
                    : CameraCharacteristics.LENS_FACING_BACK)) {
          selected = id;
          characteristics = info;
          break;
        }
      }
      if (selected == null) {
        opening = false;
        listener.error(front ? "Thiết bị không có camera trước." : "Không tìm thấy camera sau.");
        return;
      }
      StreamConfigurationMap map =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map == null) throw new IllegalStateException("Không có cấu hình camera.");
      Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
      previewSize = choose(sizes, 1920 * 1080);
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
      previewSurface = new Surface(texture.getSurfaceTexture());
      preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      preview.addTarget(previewSurface);
      preview.set(
          CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
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
    final int token = generation;
    try {
      CaptureRequest.Builder request =
          camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      request.addTarget(reader.getSurface());
      request.set(
          CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
      request.set(
          CaptureRequest.CONTROL_AE_MODE,
          flash
              ? CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
              : CaptureRequest.CONTROL_AE_MODE_ON);
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
                listener.error("Chụp ảnh thất bại. Hãy thử lại.");
              }
            }
          },
          main);
      main.postDelayed(
          () -> {
            if (token == generation && capturing) {
              capturing = false;
              listener.error("Camera phản hồi quá lâu. Hãy chụp lại.");
            }
          },
          8000);
    } catch (CameraAccessException | IllegalStateException e) {
      capturing = false;
      listener.error("Không chụp được ảnh. Hãy mở lại camera.");
    }
  }

  public void focus(float x, float y) {
    if (session == null || preview == null) return;
    try {
      Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      Integer regions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
      if (sensor == null || regions == null || regions == 0) return;
      int cx = (int) (sensor.left + (front ? 1 - y : y) * sensor.width()),
          cy = (int) (sensor.top + (1 - x) * sensor.height());
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
              preview.set(
                  CaptureRequest.CONTROL_AF_MODE,
                  CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
              session.setRepeatingRequest(preview.build(), null, main);
            } catch (CameraAccessException | IllegalStateException ignored) {
              /* Session may close while regaining continuous focus. */
            }
          },
          2500);
    } catch (CameraAccessException | IllegalStateException e) {
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
    generation++;
    opening = false;
    capturing = false;
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
