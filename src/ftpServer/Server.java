package ftpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * FTP Server class.
 * On receiving a new connection it creates a new worker thread.
 * 
 * @author Moritz Stueckler (SID 20414726)
 *
 */
public class Server {

    private int controlPort = 21;
    private ServerSocket welcomeSocket;
    private boolean serverRunning = true;
    private static Map<InetAddress, DeviceThread> devices = new HashMap<>();
    private static int dataPort = 2024;

    private static int getPort() {
        return dataPort++;
    }

    public static void main(String[] args) {
        new Server();
    }

    private Server()
    {
        try
        {
            welcomeSocket = new ServerSocket(controlPort);
        }
        catch (IOException e)
        {
            System.out.println("Could not create server socket"); 
            System.exit(-1);
        }
        
        System.out.println("FTP Server started listening on port " + controlPort);

        int noOfThreads = 0;
        
        while (serverRunning)
        {

            try
            {
                Socket client = welcomeSocket.accept();

                // Port for incoming dataConnection (for passive mode) is the controlPort + number of created threads + 1
//                int dataPort = 2024 + noOfThreads++;

                // Create new worker thread for new connection
                Connection w = new Connection(client, getPort(), devices);

                // Check if device IP exist in Devices list
                if(devices.keySet().contains(client.getInetAddress())){
                    w.setWorkerType(ThreadType.DATA);
                    devices.get(client.getInetAddress())
                            .getData()
                            .add(w);
                    System.out.println(devices);
                } else {
                    w.setWorkerType(ThreadType.INITIAL);
                    devices.put(client.getInetAddress(), new DeviceThread(w));
                    System.out.println(devices);
                }

                System.out.println("New connection received. Worker was created.");
                w.start();
            }
            catch (IOException e)
            {
                System.out.println("Exception encountered on accept");  
                e.printStackTrace();
            }
        }
        try
        {
            welcomeSocket.close();
            System.out.println("Server was stopped");
            
        } catch (IOException e)
        {
            System.out.println("Problem stopping server"); 
            System.exit(-1);
        }

    }
}

enum ThreadType {
    INITIAL, DATA
}
