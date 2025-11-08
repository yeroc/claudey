import java.net.URL;
import java.net.HttpURLConnection;

public class TestHttpsSimple {
  public static void main(String[] args) {
    try {
      System.out.println("java.net.useSystemProxies: " + System.getProperty("java.net.useSystemProxies"));
      System.out.println("https.proxyHost: " + System.getProperty("https.proxyHost"));
      System.out.println("HTTPS_PROXY env: " + (System.getenv("HTTPS_PROXY") != null ? "set" : "not set"));

      URL url = new URL("https://repo.maven.apache.org/maven2/");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(10000);
      conn.setRequestMethod("HEAD");

      int code = conn.getResponseCode();
      System.out.println("Response code: " + code);
      System.out.println("Success! Connected to " + url);
    } catch (Exception e) {
      System.out.println("HTTPS Connection Failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
