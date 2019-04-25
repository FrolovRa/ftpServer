package ftpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Interceptor {

    ServerSocket foriPhone;
    int dataPort = 2024;

    BufferedReader controlInVLC;
    PrintWriter controlOutVLC;

    Interceptor() throws IOException {
        foriPhone = new ServerSocket(21);

        System.out.println("Interceptor is running...");

        Socket client = foriPhone.accept();

        controlInVLC = new BufferedReader(new InputStreamReader(client.getInputStream()));

        controlOutVLC = new PrintWriter(client.getOutputStream(), true);

    }
}
