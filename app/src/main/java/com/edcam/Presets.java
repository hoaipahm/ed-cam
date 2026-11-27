package com.edcam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable color recipes shared by preview and export. No camera or UI dependency. */
public final class Presets {
  public static final class Style {
    public final int id;
    public final String name, group;
    private final float[] transform;
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
      transform = recipeMatrix(this, 1);
    }

    Style(int id, String name, String group, float[] transform, float grain, float vignette) {
      this.id = id;
      this.name = name;
      this.group = group;
      this.transform = transform.clone();
      this.grain = grain;
      this.vignette = vignette;
      saturation = contrast = 1;
      red = green = blue = lift = 0;
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
    "Phố Việt",
    "Duotone",
    "Neon",
    "Hoàng hôn",
    "Băng giá",
    "Nâu cổ",
    "Pop Art",
    "Hồng tím",
    "Xanh điện ảnh",
    "Đơn sắc",
    "Thử nghiệm",
    "Film đậm",
    "Ánh sáng mới"
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
    addExpressiveStyles(styles);
    ALL = Collections.unmodifiableList(styles);
  }

  private static void add(List<Style> list, String group, String[] names, float[][] recipes) {
    for (int i = 0; i < names.length; i++)
      list.add(new Style(list.size(), names[i], group, recipes[i]));
  }

  public static Style get(int id) {
    return ALL.get(Math.max(0, Math.min(ALL.size() - 1, id)));
  }

  private static float[] recipeMatrix(Style style, float strength) {
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

  /** Interpolate a cached recipe: preview, thumbnails and export use the identical transform. */
  public static float[] matrix(Style style, float strength) {
    float amount = Float.isFinite(strength) ? Math.max(0, Math.min(1, strength)) : 0;
    float[] result = new float[20];
    for (int i = 0; i < 20; i++) {
      float identity = (i == 0 || i == 6 || i == 12 || i == 18) ? 1 : 0;
      result[i] = identity + (style.transform[i] - identity) * amount;
    }
    return result;
  }

  private static float[] duotone(int shadow, int light) {
    float[] result = new float[20];
    float[] luma = {.213f, .715f, .072f};
    for (int channel = 0; channel < 3; channel++) {
      int shift = 16 - channel * 8;
      float low = (shadow >> shift) & 255, high = (light >> shift) & 255;
      for (int i = 0; i < 3; i++) result[channel * 5 + i] = (high - low) / 255 * luma[i];
      result[channel * 5 + 4] = low;
    }
    result[18] = 1;
    return result;
  }

  private static float[] hue(
      float degrees,
      float saturation,
      float contrast,
      float red,
      float green,
      float blue,
      float lift) {
    double angle = Math.toRadians(degrees);
    float c = (float) Math.cos(angle), t = (float) Math.sin(angle);
    float[] rotation = {
      .213f + c * .787f - t * .213f,
      .715f - c * .715f - t * .715f,
      .072f - c * .072f + t * .928f,
      .213f - c * .213f + t * .143f,
      .715f + c * .285f + t * .140f,
      .072f - c * .072f - t * .283f,
      .213f - c * .213f - t * .787f,
      .715f - c * .715f + t * .715f,
      .072f + c * .928f + t * .072f
    };
    float[] luma = {.213f, .715f, .072f}, result = new float[20];
    float[] offsets = {red, green, blue};
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 3; col++)
        result[row * 5 + col] =
            (rotation[row * 3 + col] * saturation + luma[col] * (1 - saturation)) * contrast;
      result[row * 5 + 4] = 128 * (1 - contrast) + lift + offsets[row];
    }
    result[18] = 1;
    return result;
  }

  private static void addExpressiveStyles(List<Style> styles) {
    String[][] names = {
      {
        "Mực đào",
        "Đêm san hô",
        "Cyanotype",
        "Tím chanh",
        "Rêu mật",
        "Hồng than",
        "Nâu ngọc",
        "Xanh giấy",
        "Đỏ kem",
        "Bạc xanh"
      },
      {
        "Neon Tokyo",
        "Đèn tím",
        "Laser xanh",
        "Club đỏ",
        "Hồng điện",
        "Acid lime",
        "Neon cam",
        "Đêm cobalt",
        "Violet city",
        "Cyber pink"
      },
      {
        "Mặt trời đỏ",
        "Chiều mật ong",
        "Cam cháy",
        "Cát hồng",
        "Nắng đồng",
        "Hoàng hôn tím",
        "Mơ vàng",
        "Đỏ đất",
        "Vàng rực",
        "Chiều nhiệt đới"
      },
      {
        "Băng xanh",
        "Tuyết lam",
        "Cực quang",
        "Sương bạc",
        "Đông cobalt",
        "Băng ngọc",
        "Trời cực",
        "Biển lạnh",
        "Đêm băng",
        "Xanh thép"
      },
      {
        "Sepia đậm",
        "Giấy trà",
        "Mực nâu",
        "Đồng cổ",
        "Gỗ trầm",
        "Nâu sữa",
        "Ảnh thuộc da",
        "Giấy cháy",
        "Cacao",
        "Bưu ảnh 1920"
      },
      {
        "Pop đỏ",
        "Pop cam",
        "Pop vàng",
        "Pop lá",
        "Pop cyan",
        "Pop lam",
        "Pop tím",
        "Pop hồng",
        "Pop acid",
        "Pop điện"
      },
      {
        "Tím mận",
        "Hồng kẹo",
        "Lilac haze",
        "Rose punch",
        "Tím khói",
        "Magenta film",
        "Hoa giấy",
        "Hồng phấn đậm",
        "Mâm xôi",
        "Chiều lavender"
      },
      {
        "Teal Orange",
        "Rạp đêm",
        "Ngọc đồng",
        "Phim trinh thám",
        "Hải cảng",
        "Xanh taxi",
        "Đêm Hong Kong",
        "Bóng petrol",
        "Biển bạc",
        "Phim viễn tưởng"
      },
      {
        "Than trắng",
        "Bạc gắt",
        "Mono đỏ",
        "Mono lam",
        "Mono xanh",
        "Platinum",
        "Graphite",
        "Mono hồng",
        "Mực vàng",
        "Đen sâu"
      },
      {
        "Đảo âm",
        "Đảo cyan",
        "Đảo đỏ",
        "Đảo tím",
        "Hoán đỏ lam",
        "Hoán lá đỏ",
        "Infra đỏ",
        "Infra lam",
        "Âm bản cam",
        "Tia X"
      },
      {
        "Film ấm đậm",
        "Film lạnh đậm",
        "Cuộn đỏ",
        "Cuộn ngọc",
        "Film nắng gắt",
        "Film tím đậm",
        "Cross process",
        "Film rêu",
        "Film đêm đậm",
        "Slide rực"
      },
      {
        "High key",
        "Low key",
        "Nắng trắng",
        "Đèn tungsten",
        "Đèn huỳnh quang",
        "Nắng cyan",
        "Ánh sân khấu",
        "Đỏ nhung",
        "Ánh ngọc",
        "Bình minh mới"
      }
    };
    int[][] shadows = {
      {
        0x271c48, 0x102d49, 0x062f65, 0x452166, 0x193f32, 0x2d132a, 0x493026, 0x173d48, 0x541822,
        0x14253e
      },
      {
        0x24160d, 0x49321c, 0x231914, 0x502b11, 0x2c2018, 0x69432f, 0x402012, 0x321a0c, 0x241610,
        0x543d2d
      },
      {
        0x080808, 0x030309, 0x301616, 0x102a46, 0x19392c, 0x24212e, 0x292929, 0x3c1730, 0x28210d,
        0x000000
      }
    };
    int[][] highlights = {
      {
        0xffd6ac, 0xff866b, 0xc3f3ff, 0xf8f098, 0xffe093, 0xff9fc3, 0x9cebd2, 0xfff1b4, 0xffe4be,
        0xd8f7ff
      },
      {
        0xffdf9f, 0xfff0c3, 0xe4b47b, 0xffc56f, 0xd3ac87, 0xffdfbd, 0xf0c18e, 0xffd092, 0xd4b49d,
        0xf4e9c9
      },
      {
        0xf8f8f8, 0xffffff, 0xffe6df, 0xe2f0ff, 0xe5ffee, 0xf2e7ff, 0xe7e7e7, 0xffe8f7, 0xfff1b8,
        0xc6c6c6
      }
    };
    for (int family = 0; family < names.length; family++) {
      for (int i = 0; i < 10; i++) {
        float[] matrix;
        float grain = 0, vignette = .08f;
        if (family == 0 || family == 4 || family == 8) {
          int palette = family == 0 ? 0 : family == 4 ? 1 : 2;
          matrix = duotone(shadows[palette][i], highlights[palette][i]);
          grain = family == 4 ? 7 : 0;
        } else {
          float angle = 0, sat = 1, contrast = 1.1f, r = 0, g = 0, b = 0, lift = 0;
          switch (family) {
            case 1:
              angle = (i - 4) * 12;
              sat = 1.65f + i * .045f;
              contrast = 1.16f;
              r = 24 - i * 4;
              g = -24 + i * 3;
              b = 32 - i * 3;
              break;
            case 2:
              angle = -14 + i * 3;
              sat = 1.1f + i * .025f;
              contrast = 1.08f;
              r = 30 + i * 2;
              g = 10 - i;
              b = -35 + i * 3;
              lift = 4;
              break;
            case 3:
              angle = -18 + i * 4;
              sat = .7f + i * .045f;
              contrast = 1.12f;
              r = -32 + i;
              g = 9 + i;
              b = 38 - i * 2;
              lift = 3;
              break;
            case 5:
              angle = i * 36;
              sat = 1.85f;
              contrast = 1.38f;
              lift = 8 - i * 2;
              break;
            case 6:
              angle = 8 + i * 5;
              sat = .82f + i * .05f;
              contrast = .88f + i * .025f;
              r = 24 + i;
              g = -26 + i * 2;
              b = 26 - i;
              lift = 12;
              break;
            case 7:
              angle = -24 + i * 5;
              sat = .72f + i * .04f;
              contrast = 1.28f;
              r = -18 + i * 4;
              g = 17 - i;
              b = 22 - i * 4;
              lift = -5;
              vignette = .26f;
              break;
            case 9:
              angle = i * 31;
              sat = 1.1f;
              contrast = i < 4 ? -1 : 1.05f;
              r = i * 4;
              g = -i * 3;
              b = 18 - i;
              break;
            case 10:
              angle = -32 + i * 7;
              sat = 1.12f + i * .05f;
              contrast = 1.2f;
              r = 20 - i * 4;
              g = -12 + i * 3;
              b = -20 + i * 5;
              lift = 8;
              grain = 8;
              vignette = .23f;
              break;
            default:
              angle = -12 + i * 3;
              sat = .7f + i * .08f;
              contrast = .7f + i * .09f;
              r = 20 - i * 3;
              g = i * 2;
              b = -18 + i * 5;
              lift = 24 - i * 5;
          }
          matrix = hue(angle, sat, contrast, r, g, b, lift);
        }
        styles.add(
            new Style(
                styles.size(), names[family][i], GROUPS[11 + family], matrix, grain, vignette));
      }
    }
  }

  private Presets() {}
}
