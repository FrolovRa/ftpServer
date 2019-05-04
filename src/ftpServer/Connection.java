package ftpServer;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Locale;


public class Connection extends Thread
{
    /**
     *  Enable debugging output to console
     */
    private boolean debugMode = true;

    private ListenPassiveModeConnection listenConnection;

    private ThreadType workerType;

    private long offset = 0;
    
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
    private String root = "i:/movie";
    private String currDirectory = "/";
    private String fileSeparator = "/";


    // control connection
    private Socket controlSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlIn;


    // data Connection
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
    public Connection(Socket client, int dataPort)
    {
        super();
        this.controlSocket = client;
        this.dataPort = dataPort;
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
            sendMsgToClient("220 Welcome to the FTP-Server");
            // Get new command from client
            while (!quitCommandLoop) {
              String a = controlIn.readLine();
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
    private void executeCommand(String c) throws InterruptedException {
        if (c == null) {
            System.out.println("null command");
            quitCommandLoop = true;
            return;
        }
        // split command and arguments
        int index = c.indexOf(' ');
        String command = ((index == -1)? c.toUpperCase() : (c.substring(0, index)).toUpperCase());
        String args = ((index == -1)? null : c.substring(index+1, c.length()));

        debugOutput("Command: " + command + " Args: " + args);
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

            case "MLSD":
                handleMlsd(args);
                break;
                
            case "PWD":
                handlePwd();
                break;

            case "NOOP":
                sendMsgToClient("200 OK");
                break;

            case "ABOR":
                handleAbor();
                break;
                
            case "QUIT":
                handleQuit();
                break;
                
            case "PASV":
                handlePasv();
                break;
            
            case "EPSV":
                handleEpsv(args);
                break;
                
            case "SYST":
                handleSyst();
                break;
                
            case "FEAT":
                handleFeat();
                break;

            case "OPTS":
                sendMsgToClient("200 OK");
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

            case "REST":
                handleRest(args);
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

    private void handleMlsd(String args) {
        String filename = root;

        if (args == null) {
            filename += currDirectory;
        } else if (args.startsWith(currDirectory)) {
            filename += args;
        } else {
            filename = currDirectory + fileSeparator + args;
        }

        File f = new File(filename);

        if(f.exists() && f.isDirectory()){
            sendMsgToClient("150 opening data connection");
        }

        sendMsgToClient("226 transfer complete ");

    }

    private void handleRest(String args) {
        offset = Long.parseLong(args);
        sendMsgToClient("350 REST parameter has been saved");
    }

    private void handleAbor() {
        closeDataConnection();
        sendMsgToClient("226 Data connection was closed");
    }


    /**
     * Sends a message to the connected client over the control connection.
     * Flushing is automatically performed by the stream.
     * @param msg The message that will be sent
     */
    private void sendMsgToClient(String msg)
    {
        if(workerType == ThreadType.INITIAL) {
            System.out.print("from INITIAL THREAD");
        }
        System.out.println("\n" + this.getName() + "-----send to client:-----------------------------" + msg + "\n");
        controlOutWriter.println(msg);
    }


    /**
     * Send a message to the connected client over the data connection.
     * @param msg Message to be sent
     */
    private void sendDataMsgToClient(File msg) {
        if (dataConnection == null || dataConnection.isClosed())  {
            sendMsgToClient("425 No data connection was established");
            debugOutput("Cannot send message, because no data connection is established");
        }
        else {
            StringBuilder response = new StringBuilder();
            if(msg.isFile())
                response.append("-");
            else if (msg.isDirectory())
                response.append("d");
            response
                    .append(" 1 2 frolovPC 4 ")
                    .append(msg.length())
                    .append(" ")
                    .append(new SimpleDateFormat("MMM dd hh:mm", Locale.ENGLISH).format(msg.lastModified()))
                    .append(" ")
                    .append(msg.getName());


            //debug
//            System.out.println(response.toString());
            dataOutWriter.print(response.toString() + '\r' + '\n');
        }
    }



//    /**
//     * Open a new data connection socket and wait for new incoming connection from client.
//     * Used for passive mode.
//     * @param port Port on which to listen for new incoming connection
//     */
//    private void openDataConnectionPassive(int port)
//    {
//        try
//        {
//            dataSocket = new ServerSocket(port);
//            System.out.println("waiting for connect... port: " + port);
//            dataConnection = dataSocket.accept();
//            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
//            debugOutput("Data connection - Passive Mode - established");
//            System.out.println("Data connection - Passive Mode - established");
//
//        } catch (IOException e)
//        {
//            debugOutput("Could not create data connection.");
//            e.printStackTrace();
//        }
//    }



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
            dataConnection = new Socket(ipAddress, port);
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Data connection - Active Mode - established");
        } catch (IOException e) {
            debugOutput("Could not connect to client data socket");
            e.printStackTrace();
        }

    }



    /**
     * Close previously established data connection sockets and streams
     */
    private void closeDataConnection(){
        try {
            if(dataOutWriter != null){
                dataOutWriter.close();
            }
            dataConnection.close();

            debugOutput("Data connection was closed");
        } catch (IOException e) {
            debugOutput("Could not close data connection");
            e.printStackTrace();
        }
        dataOutWriter = null;
        dataConnection = null;
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
        // waiting for thread that listen the connection
        try {
            listenConnection.join(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
        if (args != null && !args.equals("-l")) {
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

    private void handleSize(String args) {
        String filename;

        if (args.startsWith("/")) {
            filename  = root + args;
        } else {
            filename = root + currDirectory + args;
        }

        // Now get a File object, and see if the name we got exists
        File f = new File(filename);

        if (f.exists() && f.isFile()) {
            sendMsgToClient("213 " + f.length());
        } else {
            sendMsgToClient("550 File not found");
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
        int p1 = dataPort/256;
        int p2 = dataPort%256;



        sendMsgToClient("227 Entering Passive Mode (192,168,0,199," + p1 + "," + p2 +")" );

        listenConnection = new ListenPassiveModeConnection(this, dataPort);
        listenConnection.start();

//        if(isListening(dataPort, 500)) {
//            sendMsgToClient("227 Entering Passive Mode (192,168,0,199," + p1 + "," + p2 +")" );
//        } else {
//            System.out.println("error in new method");
//        }

    }
    
    /**
     * Handler for EPSV command which initiates extended passive mode.
     * Similar to PASV but for newer clients (IPv6 support is possible but not implemented here).
     */
    private void handleEpsv(String args) {

        if(args != null && args.toUpperCase().equals("ALL")) {

            sendMsgToClient("200 OK");

        } else {

            sendMsgToClient("229 Entering Extended Passive Mode (|||" + dataPort + "|)");
            System.out.println("used by " + this.getName() + " Thread " + workerType+ " type");

            listenConnection = new ListenPassiveModeConnection(this, dataPort);
            listenConnection.start();
        }
    }

//    private void handleEpsv() {
//        sendMsgToClient("229 Entering Extended Passive Mode (|||" + freeDataPort + "|)");
//        try {
//            dataSocket = new ServerSocket(freeDataPort);
//            System.out.println("waiting for connect... port: " + freeDataPort);
//            dataConnection = dataSocket.accept();
//            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
//        } catch (IOException e)
//        {
//            debugOutput("Could not create data connection.");
//            e.printStackTrace();
//        }
//    }
    
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
        sendMsgToClient("211-Features:");
//        sendMsgToClient("MDTM");
        sendMsgToClient("REST STREAM");
        sendMsgToClient("SIZE");
//        sendMsgToClient("MLST type*;size*;modify*;");
//        sendMsgToClient("MLSD");
        sendMsgToClient("UTF8");
//        sendMsgToClient("CLNT");
//        sendMsgToClient("MFMT");
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
    private void handleRetr(String file){
        String filename;

        if(file.startsWith(currDirectory)) {
            filename = root + file;
        } else if (file.startsWith("/")) {
            filename = root + currDirectory + file;
        } else {
            filename = root + fileSeparator + file;
        }

        try {
            listenConnection.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(filename);
        File f =  new File(filename);

        if(!f.exists()) {
            sendMsgToClient("550 File does not exist");
        } else {
            // Binary mode
            if (transferMode == transferType.BINARY) {

                sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName());

                try (BufferedOutputStream fout = new BufferedOutputStream(dataConnection.getOutputStream());
                     BufferedInputStream fin = new BufferedInputStream(new FileInputStream(f))) {
                    byte[] buf = new byte[1024];
                    int l;
                    try {
                        long skip = fin.skip(offset);
                        offset = 0;

                        debugOutput("Starting file transmission of " + f.getName() + " with skipping " + skip +" bytes");
                        while ((l = fin.read(buf, 0, 1024)) > 0) {
                            fout.write(buf, 0, l);
                        }
                    } catch (SocketException ea) {
                        debugOutput("End of writing");
                    } catch (IOException e) {
                        debugOutput("Could not read from or write to file streams");
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.getStackTrace();
                }
                debugOutput("Completed file transmission of " + f.getName());
    
                sendMsgToClient("226 File transfer successful. Closing data connection.");
    
            }
            
            // ASCII mode
            else {
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
//        closeDataConnection();
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
        if (debugMode) {
            System.out.println("Thread " + this.getName() + ": " + msg);
        }
    }

    ThreadType getWorkerType() {
        return workerType;
    }

    void setWorkerType(ThreadType workerType) {
        this.workerType = workerType;
    }

    void setDataConnection(Socket dataConnection) {
        this.dataConnection = dataConnection;
    }

    void setDataOutWriter(PrintWriter dataOutWriter) {
        this.dataOutWriter = dataOutWriter;
    }

    private boolean isListening(int port, int timeout) {
        Socket test = null;
        try {
                test = new Socket("localhost", port);
                return true;
            } catch (IOException e) {
//                e.printStackTrace();
                System.out.println("port isn't available " + e.getMessage());
                if(timeout <= 0) {
                    return false;
                } else {
                    try {
                        Thread.sleep(100);
                        timeout = timeout - 100;
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    return isListening(port, timeout);
                }
            }
            finally {
                if(test != null){
                    try {
                        test.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
    }
}
