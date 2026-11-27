package com.edcam;

import android.graphics.Color;

public final class Palette {
  public final String name, description;
  public final int background, surface, ink, muted, accent, onAccent, line;
  public final boolean dark;

  private Palette(
      String name,
      String description,
      String bg,
      String surface,
      String ink,
      String muted,
      String accent,
      String onAccent,
      String line,
      boolean dark) {
    this.name = name;
    this.description = description;
    this.background = Color.parseColor(bg);
    this.surface = Color.parseColor(surface);
    this.ink = Color.parseColor(ink);
    this.muted = Color.parseColor(muted);
    this.accent = Color.parseColor(accent);
    this.onAccent = Color.parseColor(onAccent);
    this.line = Color.parseColor(line);
    this.dark = dark;
  }

  public static final Palette[] ALL = {
    new Palette(
        "Film kem",
        "Ấm như một trang nhật ký",
        "#F7F4ED",
        "#FFFDF8",
        "#292440",
        "#6B647E",
        "#6554A4",
        "#FFFFFF",
        "#E1D9F1",
        false),
    new Palette(
        "Đêm tối",
        "Dịu mắt khi chụp ban đêm",
        "#191922",
        "#242432",
        "#F4EFFC",
        "#BEB5CF",
        "#CDC0F4",
        "#27203C",
        "#444052",
        true),
    new Palette(
        "Hiện đại",
        "Rõ ràng, gọn gàng, trong trẻo",
        "#F3F6FC",
        "#FFFFFF",
        "#1D2B44",
        "#55647B",
        "#2756BD",
        "#FFFFFF",
        "#D4DDEB",
        false),
    new Palette(
        "Hồng phấn",
        "Một chút ngọt cho mỗi ngày",
        "#FBEFF2",
        "#FFF8FA",
        "#492E3A",
        "#805568",
        "#A3355F",
        "#FFFFFF",
        "#E7CAD5",
        false),
    new Palette(
        "Xanh sage",
        "Nhẹ như một buổi dạo chơi",
        "#EDF2E9",
        "#F8FBF4",
        "#273D31",
        "#566D5C",
        "#356449",
        "#FFFFFF",
        "#CDDACA",
        false)
  };

  public static Palette get(int id) {
    return ALL[Math.max(0, Math.min(ALL.length - 1, id))];
  }
}
