import java.net.URL;
import java.net.HttpURLConnection;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;

public class TestHttps {
  public static void main(String[] args) {
    try {
      // Parse proxy from environment
      String proxyEnv = System.getenv("HTTPS_PROXY");
      if (proxyEnv != null && !proxyEnv.isEmpty()) {
        URI proxyUri = new URI(proxyEnv);
        String userInfo = proxyUri.getUserInfo();

        if (userInfo != null && userInfo.contains(":")) {
          String[] parts = userInfo.split(":", 2);
          String proxyUser = parts[0];
          String proxyPass = parts[1];

          System.out.println("Configuring proxy: " + proxyUri.getHost() + ":" + proxyUri.getPort());
          System.out.println("Proxy user: " + proxyUser);

          // Set proxy system properties
          System.setProperty("https.proxyHost", proxyUri.getHost());
          System.setProperty("https.proxyPort", String.valueOf(proxyUri.getPort()));

          // Set up proxy authentication
          Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
              System.out.println("Authenticator called! RequestorType: " + getRequestorType());
              System.out.println("Requesting host: " + getRequestingHost());
              System.out.println("Requesting port: " + getRequestingPort());
              return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
            }
          });
        }
      }

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
