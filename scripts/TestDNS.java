import java.net.InetAddress;
public class TestDNS {
    public static void main(String[] args) {
        try {
            InetAddress addr = InetAddress.getByName("repo.maven.apache.org");
            System.out.println("Resolved: " + addr.getHostAddress());
        } catch (Exception e) {
            System.out.println("DNS Resolution Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
