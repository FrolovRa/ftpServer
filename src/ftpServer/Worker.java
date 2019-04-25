package ftpServer;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Class for a FTP server worker thread.
 * 
 * @author Moritz Stueckler (SID 20414726)
 *
 */
public class Worker extends Thread
{
    /**
     *  Enable debugging output to console
     */
    private boolean debugMode = true;

    private int increment = 0;

    private threadType workerType;

    private Map<InetAddress, DeviceThread> devices;
    
    /**
     * Indicating the last set transfer Type
     */
    private enum transferType {
        ASCII, BINARY
    }
    
    /**
     * Indicates the authentification status of a user
     */
    private enum userStatus {
        NOTLOGGEDIN, ENTEREDUSERNAME, LOGGEDIN
    }


    // Path information
    private String root;
    private String currDirectory;
    private String fileSeparator = "/";


    // control connection
    private Socket controlSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlIn;


    // data Connection
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private PrintWriter dataOutWriter;
    
    private int dataPort;
    private transferType transferMode = transferType.ASCII;


    // user properly logged in?
    private userStatus currentUserStatus = userStatus.NOTLOGGEDIN;
    private String validUser = "a";
    private String validPassword = "a";
    
    private boolean quitCommandLoop = false;
    
    /**
     * Create new worker with given client socket
     * @param client the socket for the current client
//     * @param dataPort the port for the data connection
     */
    public Worker(Socket client, Map<InetAddress, DeviceThread> devices)
    {
        super();
        this.controlSocket = client;
//        this.dataPort = dataPort;
        this.currDirectory = "/";
        this.root = "i:/movie";
        this.devices = devices;
    }

    public threadType getWorkerType() {
        return workerType;
    }

    public void setWorkerType(threadType workerType) {
        this.workerType = workerType;
    }

    /**
     * Run method required by Java thread model
     */
    public void run()
    {

        try
        {
            // Input from client
            controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            // Output to client, automatically flushed after each print
            controlOutWriter = new PrintWriter(controlSocket.getOutputStream(), true);
            
            // Greeting
            sendMsgToClient("220 Welcome to the COMP4621 FTP-Server");
            // Get new command from client
            while (!quitCommandLoop) {
              String a = controlIn.readLine();

              System.out.println(a);
              executeCommand(a);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        { 
            // Clean up 
            try
            {                    
                controlIn.close(); 
                controlOutWriter.close(); 
                controlSocket.close(); 
                debugOutput("Sockets closed and worker stopped"); 
            } 
            catch(IOException e) 
            { 
                e.printStackTrace();
                debugOutput("Could not close sockets");
            } 
        } 
        
    }
    
    /**
     * Main command dispatcher method.
     * Separates the command from the arguments and dispatches it to single handler functions.
     * @param c the raw input from the socket consisting of command and arguments
     */
    private void executeCommand(String c)
    {
        if (c == null) {
            System.out.println("null command");
        }
        // split command and arguments
        int index = c.indexOf(' ');
        String command = ((index == -1)? c.toUpperCase() : (c.substring(0, index)).toUpperCase());
        String args = ((index == -1)? null : c.substring(index+1, c.length()));


        debugOutput("Command: " + command + " Args: " + args);
        System.out.println();
        // dispatcher mechanism for different commands
        switch(command) {
            case "USER":                
                handleUser(args);
                break;
                
            case "PASS":
                handlePass(args);
                break;
                
            case "CWD":
                handleCwd(args);
                break;
                
            case "LIST":
                handleNlst(args);
                break;
                
            case "NLST":
                handleNlst(args);
                break;
                
            case "PWD":
                handlePwd();
                break;
                
            case "QUIT":
                handleQuit();
                break;
                
            case "PASV":
                handlePasv();
                break;
            
            case "EPSV":
                handleEpsv();
                break;
                
            case "SYST":
                handleSyst();
                break;
                
            case "FEAT":
                handleFeat();
                break;
                
            case "PORT":
                handlePort(args);
                break;
                
            case "EPRT":
                handlePort(parseExtendedArguments(args));
                break;
            
            case "RETR":
                handleRetr(args);
                break;
                
            case "MKD":
                handleMkd(args);
                break;
                
            case "RMD":
                handleRmd(args);
                break;
            
            case "TYPE":
                handleType(args);
                break;
                
            case "STOR":
                handleStor(args);
                break;

            case "SIZE":
                handleSize(args);
                break;
                
            default:
                sendMsgToClient("501 Unknown command");
                break;
            
        }

    }

    /**
     * Sends a message to the connected client over the control connection.
     * Flushing is automatically performed by the stream.
     * @param msg The message that will be sent
     */
    private void sendMsgToClient(String msg)
    {
        if(workerType == threadType.INITIAL) {
            System.out.println("from INITIAL THREAD");
        }
        System.out.println(msg);
        controlOutWriter.println(msg);
    }
    


    /**
     * Send a message to the connected client over the data connection.
     * @param msg Message to be sent
     */
    private void sendDataMsgToClient(File msg)
    {
        if (dataConnection == null || dataConnection.isClosed())
        {
            sendMsgToClient("425 No data connection was established");
            debugOutput("Cannot send message, because no data connection is established");
        }
        else
        {


            StringBuilder response = new StringBuilder();
            if(msg.isFile()) {
                response.append("-");
            } else if(msg.isDirectory()){
                response.append("d");
            }

            response.append(" 1 2 frolovPC 4 ")
            .append(msg.length())
            .append(" ")
//            .append(new SimpleDateFormat("dd hh:mm", Locale.ENGLISH).format(msg.lastModified()))
            .append(new SimpleDateFormat("MMM dd hh:mm", Locale.ENGLISH).format(msg.lastModified()))
            .append(" ")
            .append(msg.getName());


            //debug
            System.out.println(response.toString());
            dataOutWriter.print(response.toString() + '\r' + '\n');
        }
        
    }



    /**
     * Open a new data connection socket and wait for new incoming connection from client.
     * Used for passive mode.
     * @param port Port on which to listen for new incoming connection
     */
    private void openDataConnectionPassive(int port)
    {

        try
        {
            System.out.println("in openDataConnection method " + port);
            dataSocket = new ServerSocket(port);
            System.out.println("waiting for connect...");
            dataConnection = dataSocket.accept();
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Data connection - Passive Mode - established");

        } catch (IOException e)
        {
            debugOutput("Could not create data connection.");
            e.printStackTrace();
        }

    }



    /**
     * Connect to client socket for data connection.
     * Used for active mode.
     * @param ipAddress Client IP address to connect to
     * @param port Client port to connect to
     */
    private void openDataConnectionActive(String ipAddress, int port)
    {
        try
        {
            dataConnection = new Socket("192.168.0.105", port);
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Data connection - Active Mode - established");
        } catch (IOException e)
        {
            debugOutput("Could not connect to client data socket");
            e.printStackTrace();
        }

    }



    /**
     * Close previously established data connection sockets and streams
     */
    private void closeDataConnection()
    {
        try
        {
            dataOutWriter.close();
            dataConnection.close();
            if (dataSocket != null)
            {
                dataSocket.close();
            }
            
           
            debugOutput("Data connection was closed");
        } catch (IOException e)
        {
            debugOutput("Could not close data connection");
            e.printStackTrace();
        }
        dataOutWriter = null;
        dataConnection = null;
        dataSocket = null;          
    }



    /**
     * Handler for USER command.
     * User identifies the client.
     * @param username Username entered by the user
     */
    private void handleUser(String username)
    {
        if (username.toLowerCase().equals(validUser))
        {
            sendMsgToClient("331 User name okay, need password");
            currentUserStatus = userStatus.ENTEREDUSERNAME;
        }
        else if (currentUserStatus == userStatus.LOGGEDIN)
        {
            sendMsgToClient("530 User already logged in");
        }
        else
        {
            sendMsgToClient("530 Not logged in");
        }
    }

    /**
     * Handler for PASS command.
     * PASS receives the user password and checks if it's valid.
     * @param password Password entered by the user
     */

    private void handlePass(String password)
    {
        // User has entered a valid username and password is correct
        if (currentUserStatus == userStatus.ENTEREDUSERNAME && password.equals(validPassword))
        {
            currentUserStatus = userStatus.LOGGEDIN;
            sendMsgToClient("230-Welcome to HKUST");
            sendMsgToClient("230 User logged in successfully");  
        }
        
        // User is already logged in
        else if (currentUserStatus == userStatus.LOGGEDIN)
        {
            sendMsgToClient("530 User already logged in");
        }
        
        // Wrong password
        else
        {
            sendMsgToClient("530 Not logged in");
        }
    }



    /**
     * Handler for CWD (change working directory) command.
     * @param args New directory to be created
     */
    private void handleCwd(String args) {
        String filename = currDirectory;

        if(args.equals("//") || args.equals(currDirectory)) {
            sendMsgToClient("250 " + "CWD successful. " + currDirectory + " is current directory");
            return;
        // go one level up (cd ..)
        } else if (args.equals("..")) {
            int ind = filename.lastIndexOf(fileSeparator);
            if (ind > 0) {
                filename = filename.substring(0, ind);
            }
        } else if (args.contains(currDirectory)) {
            filename = args;
        } else {
            if(args.startsWith(fileSeparator))
                filename = currDirectory + args;
            else
                filename = filename + fileSeparator + args;
        }
        // check if file exists, is directory and is not above root directory
        File f = new File(root + filename.substring(0, filename.length() -1 ));

        if (f.exists() && f.isDirectory() && (f.getAbsolutePath().length() >= root.length())) {
            currDirectory = filename;
            sendMsgToClient("250 " + "CWD successful. " + currDirectory + " is current directory");
        }
        else {
            sendMsgToClient("550 Requested action not taken. File unavailable.");
        }
    }
    
    /**
     * Handler for NLST (Named List) command.
     * Lists the directory content in a short format (names only)
     * @param args The directory to be listed
     */
    private void handleNlst(String args)
    {
        if (dataConnection == null || dataConnection.isClosed())
        {
            sendMsgToClient("425 No data connection was established");
        }
        else
        {
            
            File[] dirContent = nlstHelper(args);
            
            if (dirContent == null)
            {
                sendMsgToClient("550 File does not exist.");
            }
            else
            {
                sendMsgToClient("125 Opening ASCII mode data connection for file list.");

                for (int i = 0; i < dirContent.length; i++)
                {
                    sendDataMsgToClient(dirContent[i]);
                }
            
                sendMsgToClient("226 Transfer complete.");
                closeDataConnection();
                
            }
        }
    }
    
    /**
     * A helper for the NLST command. The directory name is obtained by 
     * appending "args" to the current directory
     * @param args The directory to list
     * @return an array containing names of files in a directory. If the given
     * name is that of a file, then return an array containing only one element
     * (this name). If the file or directory does not exist, return nul.
     */
    private File[] nlstHelper(String args)
    {
        // Construct the name of the directory to list.
        String filename = root + currDirectory;
        if (args != null)
        {
            filename = filename + fileSeparator + args;
        }
    

        // Now get a File object, and see if the name we got exists and is a
        // directory.
        File f = new File(filename);
            
        if (f.exists() && f.isDirectory())
        {
            return f.listFiles();
        }
        else if (f.exists() && f.isFile())
        {
            File[] file = new File[1];
            file[0] = f;
            return file;
        }
        else
        {
            return null;
        }
    }
    
    /**
     * Handler for the PORT command.
     * The client issues a PORT command to the server in active mode, so the
     * server can open a data connection to the client through the given address
     * and port number.
     * @param args The first four segments (separated by comma) are the IP address.
     *        The last two segments encode the port number (port = seg1*256 + seg2)
     */
    private void handlePort(String args)
    {
        // Extract IP address and port number from arguments
        String[] stringSplit = args.split(",");
        String hostName = stringSplit[0] + "." + stringSplit[1] + "." + 
                stringSplit[2] + "." + stringSplit[3];
    
        int p = Integer.parseInt(stringSplit[4])*256 + Integer.parseInt(stringSplit[5]);
        
        // Initiate data connection to client
        openDataConnectionActive(hostName, p);
        sendMsgToClient("200 Command OK");
        
    }

    
    /**
     * Handler for PWD (Print working directory) command.
     * Returns the path of the current directory back to the client.
     */
    private void handlePwd()
    {
        sendMsgToClient("257 \"" + currDirectory + "\"");
    }

    private void handleSize(String args)
    {
        String filename = root + fileSeparator + args;

        // Now get a File object, and see if the name we got exists
        File f = new File(filename);

        if (f.exists() && f.isFile())
        {
            sendMsgToClient("213 " + f.length());
        } else {
            sendMsgToClient("550");
        }
    }
    
    /**
     * Handler for PASV command which initiates the passive mode.
     * In passive mode the client initiates the data connection to the server.
     * In active mode the server initiates the data connection to the client.
     */
    private void handlePasv()
    {
        // Using fixed IP for connections on the same machine
        // For usage on separate hosts, we'd need to get the local IP address from somewhere
        // Java sockets did not offer a good method for this
//        String myIp = "192.168.0.199";
//        String myIpSplit[] = myIp.split("\\.");
//        dataPort = dataPort + increment;
        int a = Server.getPort();
//        int p1 = dataPort/256;
        int p1 = a/256;
//        int p2 = dataPort%256;
        int p2 = a%256;

        sendMsgToClient("227 Entering Passive Mode (192,168,0,199," + p1 + "," + p2 +")" );
        
        openDataConnectionPassive(a);
//        openDataConnectionPassive(dataPort);
//        increment++;

    }
    
    /**
     * Handler for EPSV command which initiates extended passive mode.
     * Similar to PASV but for newer clients (IPv6 support is possible but not implemented here).
     */
    private void handleEpsv() {
        int a = Server.getPort();
        devices.get(controlSocket.getInetAddress())
                .getInitialThread()
                .sendMsgToClient("229 Entering Extended Passive Mode (|||" + a + "|)");
//        sendMsgToClient("229 Entering Extended Passive Mode (|||" + a + "|)");
        System.out.println("used by " + this.getName() + " Thread " + workerType+ " type");

//        sendMsgToClient("229 Entering Extended Passive Mode (|||" + dataPort + "|)");
        openDataConnectionPassive(a);

    }
    
    /**
     * Handler for the QUIT command.
     */
    private void handleQuit()
    {
        sendMsgToClient("221 Closing connection");
        quitCommandLoop = true;
    }
    
    private void handleSyst()
    {
        sendMsgToClient("215 UNIX Type: L8");
    }
    
    /**
     * Handler for the FEAT (features) command.
     * Feat transmits the abilities/features of the server to the client.
     * Needed for some ftp clients.
     * This is just a dummy message to satisfy clients, no real feature information included.
     */
    private void handleFeat()
    {
        sendMsgToClient("211-Extensions supported:");
        sendMsgToClient("EPLF");
        sendMsgToClient("EPSV");
        sendMsgToClient("EPRT");
        sendMsgToClient("211 END");
    }
    
    /**
     * Handler for the MKD (make directory) command.
     * Creates a new directory on the server.
     * @param args Directory name
     */
    private void handleMkd(String args)
    {
        // Allow only alphanumeric characters
        if (args != null && args.matches("^[a-zA-Z0-9]+$"))
        {
            File dir = new File(currDirectory + fileSeparator + args);
            
            if(!dir.mkdir())
            {
                sendMsgToClient("550 Failed to create new directory");
                debugOutput("Failed to create new directory");
            }
            else
            {
                sendMsgToClient("250 Directory successfully created");
            }
        }
        else
        {
            sendMsgToClient("550 Invalid name");
        }
        
    }
    
    /**
     * Handler for RMD (remove directory) command.
     * Removes a directory.
     * @param dir directory to be deleted.
     */
    private void handleRmd(String dir)
    {  
        String filename = currDirectory;
        
        // only alphanumeric folder names are allowed
        if (dir != null && dir.matches("^[a-zA-Z0-9]+$"))
        {
            filename = filename + fileSeparator + dir;
            
            // check if file exists, is directory
            File d = new File(filename);
        
            if (d.exists() && d.isDirectory())
            {
                d.delete();
                
                sendMsgToClient("250 Directory was successfully removed");
            }
            else
            {
                sendMsgToClient("550 Requested action not taken. File unavailable.");
            }
        }
        else
        {
            sendMsgToClient("550 Invalid file name.");
        }
           
    }
    
    /**
     * Handler for the TYPE command.
     * The type command sets the transfer mode to either binary or ascii mode
     * @param mode Transfer mode: "a" for Ascii. "i" for image/binary.
     */
    private void handleType(String mode)
    {
        switch (mode.toUpperCase()) {
            case "A":
                transferMode = transferType.ASCII;
                sendMsgToClient("200");
                break;
            case "I":
                transferMode = transferType.BINARY;
                sendMsgToClient("200 OK");
                break;
            default:
                sendMsgToClient("504 Not OK");
                break;
        }
    }
    
    /**
     * Handler for the RETR (retrieve) command.
     * Retrieve transfers a file from the ftp server to the client.
     * @param file The file to transfer to the user
     */
    private void handleRetr(String file)
    {
        File f =  new File(root + file);
//        File f =  new File(currDirectory + fileSeparator + file);

        if(!f.exists())
        {
            sendMsgToClient("550 File does not exist");
        }
    
        else
        {
            
            // Binary mode
            if (transferMode == transferType.BINARY)
            {
                BufferedOutputStream fout = null;
                BufferedInputStream fin = null;
                
                sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName());
                
                try
                {
                    //create streams
                    fout = new BufferedOutputStream(dataConnection.getOutputStream());
                    fin = new BufferedInputStream(new FileInputStream(f));
                }
                catch (Exception e)
                {
                    debugOutput("Could not create file streams");
                }
                    
                debugOutput("Starting file transmission of " + f.getName());
                
                // write file with buffer
                byte[] buf = new byte[1024];
                int l = 0;
                try
                {
                    while ((l = fin.read(buf,0,1024)) != -1)
                    {
                        fout.write(buf,0,l);
                    }
                }
                catch (IOException e)
                {
                    debugOutput("Could not read from or write to file streams");
                    e.printStackTrace();
                }
                
                //close streams
                try
                {
                    fin.close();
                    fout.close();
                } catch (IOException e)
                {
                    debugOutput("Could not close file streams");
                    e.printStackTrace();
                }
                
                    
                debugOutput("Completed file transmission of " + f.getName());
    
                sendMsgToClient("226 File transfer successful. Closing data connection.");
    
            }
            
            // ASCII mode
            else
            {
                sendMsgToClient("150 Opening ASCII mode data connection for requested file " + f.getName());
    
                BufferedReader rin = null;
                PrintWriter rout = null;
                
                try
                {
                    rin = new BufferedReader(new FileReader(f));
                    rout = new PrintWriter(dataConnection.getOutputStream(),true);
                    
                }
                catch (IOException e)
                {
                    debugOutput("Could not create file streams");
                }
                
                String s;
                
                try
                {
                    while((s = rin.readLine()) != null)
                    {
                        rout.println(s);
                    }
                } catch (IOException e)
                {
                    debugOutput("Could not read from or write to file streams");
                    e.printStackTrace();
                }
                
                try
                {
                    rout.close();
                    rin.close();
                } catch (IOException e)
                {
                    debugOutput("Could not close file streams");
                    e.printStackTrace();
                }
                sendMsgToClient("226 File transfer successful. Closing data connection.");
            }
    
        }
        closeDataConnection();
    
    }
    
    /**
     * Handler for STOR (Store) command.
     * Store receives a file from the client and saves it to the ftp server.
     * @param file The file that the user wants to store on the server
     */
    private void handleStor(String file)
    {
        if (file == null)
        {
            sendMsgToClient("501 No filename given");
        }
        else
        {
            File f =  new File(currDirectory + fileSeparator + file);

            if(f.exists())
            {
                sendMsgToClient("550 File already exists");
            }

            else
            {

                // Binary mode
                if (transferMode == transferType.BINARY)
                {
                    BufferedOutputStream fout = null;
                    BufferedInputStream fin = null;
                    
                    sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName());

                    try
                    {
                        // create streams
                        fout = new BufferedOutputStream(new FileOutputStream(f));
                        fin = new BufferedInputStream(dataConnection.getInputStream());
                    }
                    catch (Exception e)
                    {
                        debugOutput("Could not create file streams");
                    }

                    debugOutput("Start receiving file " + f.getName());

                    // write file with buffer
                    byte[] buf = new byte[1024];
                    int l = 0;
                    try
                    {
                        while ((l = fin.read(buf,0,1024)) != -1)
                        {
                            fout.write(buf,0,l);
                        }
                    }
                    catch (IOException e)
                    {
                        debugOutput("Could not read from or write to file streams");
                        e.printStackTrace();
                    }

                    //close streams
                    try
                    {
                        fin.close();
                        fout.close();
                    } catch (IOException e)
                    {
                        debugOutput("Could not close file streams");
                        e.printStackTrace();
                    }


                    debugOutput("Completed receiving file " + f.getName());

                    sendMsgToClient("226 File transfer successful. Closing data connection.");

                }

                // ASCII mode
                else
                {
                    sendMsgToClient("150 Opening ASCII mode data connection for requested file " + f.getName());

                    BufferedReader rin = null;
                    PrintWriter rout = null;

                    try
                    {
                        rin = new BufferedReader(new InputStreamReader(dataConnection.getInputStream()));
                        rout = new PrintWriter(new FileOutputStream(f),true);

                    }
                    catch (IOException e)
                    {
                        debugOutput("Could not create file streams");
                    }

                    String s;

                    try
                    {
                        while((s = rin.readLine()) != null)
                        {
                            rout.println(s);
                        }
                    } catch (IOException e)
                    {
                        debugOutput("Could not read from or write to file streams");
                        e.printStackTrace();
                    }

                    try
                    {
                        rout.close();
                        rin.close();
                    } catch (IOException e)
                    {
                        debugOutput("Could not close file streams");
                        e.printStackTrace();
                    }
                    sendMsgToClient("226 File transfer successful. Closing data connection.");
                }

            }
            closeDataConnection();
        }

    }

    /**
     * Helper method to parse the arguments of the EXXX commands (e.g. EPRT).
     * EXXX commands are newer and support IPv6 (not supported here). The arguments
     * get translated back to a "regular" argument.
     * @param extArg The extended argument
     * @return The regular argument
     */

    private String parseExtendedArguments(String extArg)
    {
        String[] splitArgs = extArg.split("\\|");
        String ipAddress = splitArgs[2].replace('.', ',');
        int port = Integer.parseInt(splitArgs[3]);
        int p1 = port/256;
        int p2 = port%256;
         
        return ipAddress + "," + p1 + "," + p2;
        
    }
    
    /**
     * Debug output to the console. Also includes the Thread ID for better readability.
     * @param msg Debug message
     */
    private void debugOutput(String msg)
    {
        if (debugMode)
        {
            System.out.println("Thread " + this.getId() + ": " + msg);
        }
    }
    
    

}
