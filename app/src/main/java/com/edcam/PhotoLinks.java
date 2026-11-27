package com.edcam;

import android.content.SharedPreferences;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Link credentials always stay on the configured HTTPS origin. */
final class PhotoLinks {
  static String origin(String input) throws IOException {
    try {
      URI u = URI.create(input.trim());
      if (!"https".equals(u.getScheme())
          || u.getHost() == null
          || u.getUserInfo() != null
          || u.getQuery() != null
          || u.getFragment() != null
          || !(u.getPath().isEmpty() || u.getPath().equals("/")))
        throw new IllegalArgumentException();
      return "https://" + u.getRawAuthority();
    } catch (IllegalArgumentException e) {
      throw new IOException("Cần địa chỉ HTTPS của máy chủ Ed-Cam.");
    }
  }

  static JSONObject request(String url, String method, String credential, JSONObject body)
      throws Exception {
    HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
    try {
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(12000);
      c.setReadTimeout(30000);
      c.setRequestMethod(method);
      c.setRequestProperty("Authorization", "Bearer " + credential);
      if (body != null) {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) {
          out.write(bytes);
        }
      }
      int code = c.getResponseCode();
      if (code == 404) throw new FileNotFoundException("Liên kết đã hết hạn hoặc được thu hồi.");
      if (code < 200 || code >= 300) throw new IOException("Máy chủ trả mã " + code + ".");
      try (InputStream in = c.getInputStream()) {
        return new JSONObject(new java.util.Scanner(in, "UTF-8").useDelimiter("\\A").next());
      }
    } finally {
      c.disconnect();
    }
  }

  static JSONObject create(SharedPreferences prefs, JSONArray images) throws Exception {
    String base = origin(prefs.getString("share-origin", ""));
    JSONObject result =
        request(
            base + "/shares",
            "POST",
            prefs.getString("share-key", ""),
            new JSONObject().put("images", images));
    String id = result.getString("id");
    if (!id.matches("[a-zA-Z0-9_-]{24}")) throw new IOException("Liên kết không hợp lệ.");
    result.put("url", base + "/s/" + id);
    return result;
  }

  static JSONObject image(String name, byte[] jpeg) throws JSONException {
    return new JSONObject()
        .put("label", name)
        .put("jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP));
  }
}
