package ftpServer;

import java.util.ArrayList;
import java.util.Objects;

public class DeviceThread {
    private Connection initialThread;
    private ArrayList<Connection> data = new ArrayList<>();

    DeviceThread(Connection initialThread) {
        this.initialThread = initialThread;
    }

    public Connection getInitialThread() {
        return initialThread;
    }

    public void setInitialThread(Connection initialThread) {
        this.initialThread = initialThread;
    }

    public ArrayList<Connection> getData() {
        return data;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceThread)) return false;
        DeviceThread that = (DeviceThread) o;
        return Objects.equals(initialThread, that.initialThread) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialThread, data);
    }

    @Override
    public String toString() {
        return initialThread.getWorkerType() + initialThread.getName() + "    Workers in data list: " + data.size();
    }
}
