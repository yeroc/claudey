import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class TestHttps {
  public static void main(String[] args) {
    try {
      // CRITICAL: Enable Basic auth for HTTPS CONNECT tunneling
      // By default, Java 17+ disables Basic auth for proxy tunneling due to security concerns
      // (credentials sent in cleartext over the tunnel). This must be explicitly enabled.
      System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");

      // Parse proxy from environment (format: http://user:jwt_token@host:port)
      URI proxyUri = new URI(System.getenv("HTTPS_PROXY"));
      String userInfo = proxyUri.getUserInfo();

      System.out.println("Proxy: " + proxyUri.getHost() + ":" + proxyUri.getPort());

      // Create HttpClient with proxy but NO Authenticator
      // IMPORTANT: Using Authenticator causes Java to strip the Proxy-Authorization header
      // due to a bug in Java 17+ (see JDK-8306745)
      HttpClient client = HttpClient.newBuilder()
        .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
        .connectTimeout(Duration.ofSeconds(10))
        .build();

      // Manually create Proxy-Authorization header using Basic auth
      // This uses the full "username:jwt_token" from the proxy URL, just like curl
      String encodedAuth = Base64.getEncoder().encodeToString(userInfo.getBytes());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI("https://repo.maven.apache.org/maven2/"))
        .header("Proxy-Authorization", "Basic " + encodedAuth)
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

      System.out.println("Response code: " + response.statusCode());
      System.out.println("Success!");
    } catch (Exception e) {
      System.out.println("Failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
