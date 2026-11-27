package com.edcam;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.util.Base64;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.*;
import org.json.*;

/** Trust only the ephemeral localhost certificate supplied by the isolated HTTPS test. */
public final class ShareInstrumentation extends Instrumentation {
  private Bundle args;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    args = b;
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    int code = Activity.RESULT_CANCELED;
    SSLSocketFactory before = HttpsURLConnection.getDefaultSSLSocketFactory();
    SharedPreferences prefs = getTargetContext().getSharedPreferences("share-fixture", 0);
    try {
      KeyStore keys = KeyStore.getInstance(KeyStore.getDefaultType());
      keys.load(null);
      keys.setCertificateEntry(
          "local",
          CertificateFactory.getInstance("X.509")
              .generateCertificate(
                  new ByteArrayInputStream(Base64.decode(args.getString("cert"), Base64.DEFAULT))));
      TrustManagerFactory trust =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trust.init(keys);
      SSLContext tls = SSLContext.getInstance("TLS");
      tls.init(null, trust.getTrustManagers(), null);
      HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
      prefs
          .edit()
          .putString("share-origin", "https://localhost:" + args.getString("port"))
          .putString("share-key", args.getString("key"))
          .commit();
      Bitmap b = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
      b.eraseColor(0xffbc7070);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      b.compress(Bitmap.CompressFormat.JPEG, 85, out);
      b.recycle();
      JSONArray images =
          new JSONArray()
              .put(PhotoLinks.image("Nắng", out.toByteArray()))
              .put(PhotoLinks.image("Film", out.toByteArray()));
      JSONObject link = PhotoLinks.create(prefs, images);
      String url = link.getString("url");
      if (PhotoLinks.request(url + "/votes", "GET", link.getString("owner"), null)
              .getJSONArray("results")
              .length()
          != 2) throw new AssertionError();
      PhotoLinks.request(url, "DELETE", link.getString("owner"), null);
      try {
        PhotoLinks.request(url + "/info", "GET", "", null);
        throw new AssertionError("Missing revocation");
      } catch (FileNotFoundException expected) {
      }
      result.putString("stream", "PASS Android HTTPS create, owner results and revoke");
      code = Activity.RESULT_OK;
    } catch (Throwable t) {
      result.putString("stream", android.util.Log.getStackTraceString(t));
    } finally {
      prefs.edit().clear().commit();
      HttpsURLConnection.setDefaultSSLSocketFactory(before);
    }
    finish(code, result);
  }
}
