package com.edcam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable color recipes shared by preview and export. No camera or UI dependency. */
public final class Presets {
  public static final class Style {
    public final int id;
    public final String name, group;
    public final float saturation, contrast, red, green, blue, lift, grain, vignette;

    Style(int id, String name, String group, float[] recipe) {
      this.id = id;
      this.name = name;
      this.group = group;
      saturation = recipe[0];
      contrast = recipe[1];
      red = recipe[2];
      green = recipe[3];
      blue = recipe[4];
      lift = recipe[5];
      grain = recipe[6];
      vignette = recipe[7];
    }
  }

  public static final String[] GROUPS = {
    "Tất cả",
    "Tự nhiên",
    "Film",
    "Retro",
    "Y2K",
    "Điện ảnh",
    "Mộng mơ",
    "Đen trắng",
    "Phong cảnh",
    "Chân dung",
    "Phố Việt"
  };
  public static final List<Style> ALL;

  static {
    List<Style> styles = new ArrayList<>();
    add(
        styles,
        "Tự nhiên",
        new String[] {"Nguyên bản", "Trong veo", "Sắc nét", "Ánh sáng", "Dịu nhẹ", "Màu thật"},
        new float[][] {
          {1, 1, 0, 0, 0, 0, 0, 0},
          {1.1f, 1.04f, 0, 1, 3, 2, 0, 0},
          {1.12f, 1.16f, 1, 0, -1, 0, 0, 0},
          {1.02f, .95f, 3, 2, 1, 12, 0, 0},
          {.88f, .92f, 2, 1, 0, 8, 0, 0},
          {1.03f, 1.02f, 1, 0, 0, 0, 0, 0}
        });
    add(
        styles,
        "Film",
        new String[] {"Nắng 35", "Film xanh", "Cuộn hè", "Âm bản", "Film mật", "Ngày cũ"},
        new float[][] {
          {.88f, .94f, 12, 4, -9, 6, 6, .18f},
          {.8f, 1.05f, -3, 6, 9, 4, 7, .16f},
          {1.12f, 1.03f, 9, 5, -7, 3, 5, .1f},
          {.9f, .85f, 5, 6, -3, 12, 8, .2f},
          {.77f, 1.02f, 16, 7, -12, 4, 7, .22f},
          {.7f, .82f, 9, 3, -6, 16, 9, .25f}
        });
    add(
        styles,
        "Retro",
        new String[] {
          "Hoài niệm", "Bưu thiếp", "Phai nắng", "Thập niên 90", "Màu hổ phách", "Gác mái"
        },
        new float[][] {
          {.65f, .85f, 14, 6, -8, 13, 8, .24f},
          {.85f, 1.08f, 10, 4, -6, 7, 5, .18f},
          {.6f, .78f, 15, 8, -5, 22, 7, .12f},
          {.9f, 1.12f, 7, -3, 5, 4, 10, .28f},
          {.68f, 1.06f, 20, 7, -15, 5, 7, .28f},
          {.52f, .9f, 9, 3, -8, 9, 12, .3f}
        });
    add(
        styles,
        "Y2K",
        new String[] {
          "Pocket 2000", "Flash party", "Pixel pop", "CCD lạnh", "Đêm tiệc", "Candy cam"
        },
        new float[][] {
          {1.2f, 1.15f, 2, 0, 5, 0, 5, .12f},
          {.92f, 1.2f, 5, 2, 4, 10, 3, .2f},
          {1.4f, 1.1f, 5, -2, 7, 0, 4, .1f},
          {.85f, 1.12f, -8, 1, 17, 4, 5, .18f},
          {1.15f, 1.22f, 10, -8, 14, -5, 8, .3f},
          {1.22f, .92f, 12, -3, 9, 12, 2, .1f}
        });
    add(
        styles,
        "Điện ảnh",
        new String[] {"Màn bạc", "Teal & Gold", "Phố lạnh", "Nắng cuối", "Midnight", "Kịch tính"},
        new float[][] {
          {.7f, 1.14f, 5, 2, -5, -3, 4, .3f},
          {.85f, 1.18f, 8, 5, -10, -4, 3, .24f},
          {.65f, 1.15f, -9, 1, 12, -3, 4, .25f},
          {.85f, 1.12f, 18, 6, -15, 3, 4, .2f},
          {.6f, 1.22f, -8, -2, 14, -10, 5, .38f},
          {.75f, 1.35f, 4, 0, -5, -5, 5, .35f}
        });
    add(
        styles,
        "Mộng mơ",
        new String[] {"Mây hồng", "Sương sớm", "Lavender", "Giấc trưa", "Pastel", "Ánh trăng"},
        new float[][] {
          {.75f, .78f, 13, 0, 6, 22, 0, .1f},
          {.6f, .75f, 0, 4, 7, 25, 0, .08f},
          {.72f, .86f, 8, -6, 19, 17, 1, .12f},
          {.68f, .82f, 14, 8, 0, 20, 1, .1f},
          {.6f, .8f, 8, 3, 8, 26, 0, .05f},
          {.52f, .9f, -4, 0, 15, 12, 2, .2f}
        });
    add(
        styles,
        "Đen trắng",
        new String[] {"Mono", "Bạc cổ", "Noir", "Hạt bạc", "Mực", "Sương xám"},
        new float[][] {
          {0, 1.05f, 0, 0, 0, 0, 0, .1f},
          {0, .9f, 8, 5, 0, 9, 8, .2f},
          {0, 1.4f, 0, 0, 0, -7, 4, .4f},
          {0, 1.17f, 1, 1, 1, 3, 14, .2f},
          {0, 1.6f, 0, 0, 0, -3, 2, .3f},
          {0, .72f, 0, 0, 0, 22, 5, .12f}
        });
    add(
        styles,
        "Phong cảnh",
        new String[] {"Biển xanh", "Rừng sâu", "Sa mạc", "Trời thu", "Sớm mai", "Đồi xa"},
        new float[][] {
          {1.2f, 1.08f, -5, 3, 13, 3, 0, .1f},
          {1.08f, 1.14f, -5, 8, -5, -1, 1, .18f},
          {.8f, 1.12f, 18, 8, -12, 5, 2, .15f},
          {1.1f, 1.04f, 13, 5, -8, 5, 2, .12f},
          {1.04f, .9f, 8, 5, 0, 15, 0, .08f},
          {.8f, .88f, -1, 6, 7, 12, 2, .12f}
        });
    add(
        styles,
        "Chân dung",
        new String[] {"Da tự nhiên", "Peach", "Studio", "Rose", "Ánh cửa sổ", "Nắng trên vai"},
        new float[][] {
          {.93f, .95f, 5, 2, 0, 6, 0, 0},
          {.87f, .9f, 12, 3, -2, 12, 0, .08f},
          {.9f, 1.08f, 3, 1, 0, 4, 0, .15f},
          {.85f, .93f, 10, -3, 4, 10, 0, .1f},
          {.8f, .88f, 4, 2, 3, 16, 0, .12f},
          {.88f, 1.02f, 15, 6, -7, 9, 1, .15f}
        });
    add(
        styles,
        "Phố Việt",
        new String[] {"Cà phê sáng", "Hẻm nhỏ", "Mưa Sài Gòn", "Chiều Hà Nội", "Đà Lạt", "Hội An"},
        new float[][] {
          {.76f, 1.04f, 13, 6, -8, 6, 5, .22f},
          {.85f, 1.16f, 8, 3, -5, -1, 6, .25f},
          {.65f, .96f, -7, 3, 12, 5, 5, .22f},
          {.78f, .91f, 12, 6, -7, 14, 6, .15f},
          {.68f, .82f, -2, 7, 9, 18, 4, .16f},
          {1.05f, 1.1f, 17, 7, -12, 4, 4, .18f}
        });
    ALL = Collections.unmodifiableList(styles);
  }

  private static void add(List<Style> list, String group, String[] names, float[][] recipes) {
    for (int i = 0; i < names.length; i++)
      list.add(new Style(list.size(), names[i], group, recipes[i]));
  }

  public static Style get(int id) {
    return ALL.get(Math.max(0, Math.min(ALL.size() - 1, id)));
  }

  public static float[] matrix(Style style, float strength) {
    float amount = Math.max(0, Math.min(1, strength));
    float sat = 1 + (style.saturation - 1) * amount;
    float contrast = 1 + (style.contrast - 1) * amount;
    float r = .213f * (1 - sat), g = .715f * (1 - sat), b = .072f * (1 - sat);
    float base = 128 * (1 - contrast) + style.lift * amount;
    return new float[] {
      (r + sat) * contrast,
      g * contrast,
      b * contrast,
      0,
      base + style.red * amount,
      r * contrast,
      (g + sat) * contrast,
      b * contrast,
      0,
      base + style.green * amount,
      r * contrast,
      g * contrast,
      (b + sat) * contrast,
      0,
      base + style.blue * amount,
      0,
      0,
      0,
      1,
      0
    };
  }

  private Presets() {}
}
