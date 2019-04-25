package ftpServer;

import java.util.ArrayList;
import java.util.Objects;

public class DeviceThread {
    private Worker initialThread;
    private ArrayList<Worker> data = new ArrayList<>();

    DeviceThread(Worker initialThread) {
        this.initialThread = initialThread;
    }

    public Worker getInitialThread() {
        return initialThread;
    }

    public void setInitialThread(Worker initialThread) {
        this.initialThread = initialThread;
    }

    public ArrayList<Worker> getData() {
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
        return initialThread.getWorkerType() + initialThread.getName() + "\n" + data.size();
    }
}
