package ftpServer;

public class serc {
    private static serc ourInstance = new serc();

    public static serc getInstance() {
        return ourInstance;
    }

    private serc() {
    }
}
