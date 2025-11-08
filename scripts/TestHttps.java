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
      // Parse proxy from environment
      URI proxyUri = new URI(System.getenv("HTTPS_PROXY"));
      String userInfo = proxyUri.getUserInfo();

      System.out.println("Proxy: " + proxyUri.getHost() + ":" + proxyUri.getPort());

      // Create HttpClient with proxy (NO authenticator)
      HttpClient client = HttpClient.newBuilder()
        .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
        .connectTimeout(Duration.ofSeconds(10))
        .build();

      // Use Basic auth with full username:jwt credentials like curl does
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
