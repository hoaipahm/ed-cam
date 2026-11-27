package com.edcam;

import android.media.MediaRecorder;
import android.util.Size;
import android.view.Surface;
import java.io.*;
import java.nio.file.Files;

/** Recorder ownership stays with one camera generation; prepare/finalize run off the UI thread. */
final class VideoRecorder {
  final File pending;
  private final VideoStore store;
  private MediaRecorder recorder;
  private boolean started, finished;

  VideoRecorder(VideoStore store) {
    this.store = store;
    pending = store.pending();
  }

  @SuppressWarnings("deprecation")
  synchronized void prepare(Size size, int orientation, boolean audio, Runnable limit)
      throws IOException {
    if (finished) throw new IOException("Đã hủy quay video.");
    recorder = new MediaRecorder();
    if (audio) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
    recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setOutputFile(pending.getPath());
    recorder.setVideoEncodingBitRate(
        size.getWidth() * size.getHeight() >= 1920 * 1080 ? 12_000_000 : 6_000_000);
    recorder.setVideoFrameRate(30);
    recorder.setVideoSize(size.getWidth(), size.getHeight());
    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
    if (audio) {
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      recorder.setAudioEncodingBitRate(128000);
      recorder.setAudioSamplingRate(44100);
    }
    recorder.setOrientationHint(orientation);
    recorder.setMaxFileSize(2_000_000_000L);
    recorder.setOnInfoListener(
        (r, what, extra) -> {
          if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) limit.run();
        });
    recorder.setOnErrorListener((r, what, extra) -> limit.run());
    recorder.prepare();
  }

  synchronized Surface surface() {
    return recorder.getSurface();
  }

  synchronized void start() {
    recorder.start();
    started = true;
  }

  synchronized void pause(boolean paused) {
    if (paused) recorder.pause();
    else recorder.resume();
  }

  synchronized File finish() throws IOException {
    if (finished) return null;
    finished = true;
    boolean valid = started;
    try {
      if (started) recorder.stop();
    } catch (RuntimeException e) {
      valid = false;
    } finally {
      if (recorder != null) {
        recorder.release();
        recorder = null;
      }
    }
    if (valid) {
      try {
        return store.finish(pending);
      } catch (IOException e) {
        Files.deleteIfExists(pending.toPath());
        throw e;
      }
    }
    Files.deleteIfExists(pending.toPath());
    if (started) throw new IOException("Video quá ngắn hoặc chưa có dữ liệu. Hãy quay lại.");
    return null;
  }
}
