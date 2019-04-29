package ftpServer;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ListenPassiveModeConnection extends Thread {

    private Connection w;
    private int port;

    ListenPassiveModeConnection(Connection w, int port){
        this.w = w;
        this.port = port;
    }

    @Override
    public void run() {
        try
        {
            // data Connection
            ServerSocket dataSocket = new ServerSocket(port);
            System.out.println("waiting for connect... port: " + port);
            Socket dataConnection = dataSocket.accept();
            w.setDataConnection(dataConnection);
            w.setDataOutWriter(new PrintWriter(dataConnection.getOutputStream(), true));
            System.out.println("Data connection established");
            dataSocket.close();

        } catch (IOException e)   {
            System.out.println("Cannot create connection!");
            e.printStackTrace();
        }
    }
}
