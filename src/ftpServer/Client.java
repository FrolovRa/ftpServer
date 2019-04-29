package ftpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    Socket client = null;

    Client() {
        try {
            client = new Socket("localhost", 21);
            System.out.println("client is connected!");
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            out.println("USER a");
            System.out.println(in.readLine());
            out.println("PASS a");
            System.out.println(in.readLine());
            out.println("PWD");
            System.out.println(in.readLine());
            out.println("PASV ");
            String a = in.readLine();
            System.out.println(a);
            Socket data = new Socket("localhost", 2024);
            out.println("LIST");
            BufferedReader inData = new BufferedReader(new InputStreamReader(data.getInputStream()));
            String b = inData.readLine();
            while (b != null) {
                System.out.println(b);
                b = inData.readLine();
            }
//            out.print("USER a" + "\r" + "\n");
//            System.out.println(in.readLine());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Client();
    }
}
