import com.edcam.Presets;
import java.util.Arrays;
import java.util.HashSet;

public final class PresetTest {
  private static int checks;

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    check(Presets.ALL.size() == 180, "180 authored recipes");
    HashSet<String> names = new HashSet<>(), recipes = new HashSet<>();
    float[] identity = {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
    for (Presets.Style s : Presets.ALL) {
      check(names.add(s.name), "Duplicate name: " + s.name);
      check(recipes.add(Arrays.toString(Presets.matrix(s, 1))), "Duplicate transform: " + s.name);
      check(
          Arrays.equals(identity, Presets.matrix(s, 0)),
          "Strength zero must preserve original: " + s.name);
      check(Arrays.equals(Presets.matrix(s, 0), Presets.matrix(s, -1)), "Negative strength clamps");
      check(Arrays.equals(Presets.matrix(s, 1), Presets.matrix(s, 2)), "High strength clamps");
      for (float n : Presets.matrix(s, .73f)) check(Float.isFinite(n), "Finite transform");
      check(Presets.matrix(s, 1)[18] == 1, "Alpha preserved");
    }
    for (int id = 60; id < 180; id++) {
      float[] matrix = Presets.matrix(Presets.get(id), 1);
      double distance = 0;
      int[][] samples = {{32, 60, 100}, {120, 170, 90}, {220, 130, 80}, {230, 220, 200}};
      for (int[] rgb : samples)
        for (int c = 0; c < 3; c++) {
          int offset = c * 5;
          double output =
              Math.max(
                  0,
                  Math.min(
                      255,
                      matrix[offset] * rgb[0]
                          + matrix[offset + 1] * rgb[1]
                          + matrix[offset + 2] * rgb[2]
                          + matrix[offset + 4]));
          distance += Math.abs(output - rgb[c]);
        }
      check(distance / 12 > 10, "New style visibly differs from original: " + Presets.get(id).name);
      check(
          Arrays.equals(identity, Presets.matrix(Presets.get(id), Float.NaN)),
          "NaN safely returns original");
    }
    check(Presets.get(-99).id == 0, "Invalid low id");
    check(Presets.get(999).id == 179, "Invalid high id");
    check(Arrays.equals(identity, Presets.matrix(Presets.get(0), 1)), "Original recipe identity");
    for (String group : Presets.GROUPS)
      if (!group.equals("Tất cả"))
        check(
            Presets.ALL.stream().filter(s -> s.group.equals(group)).count()
                == (Arrays.asList(Presets.GROUPS).indexOf(group) <= 10 ? 6 : 10),
            "Expected style count in " + group);
    System.out.println(
        "PASS: "
            + checks
            + " checks; 180 distinct color recipes, bounds, alpha, groups and identity.");
  }
}
