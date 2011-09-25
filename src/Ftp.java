/**
 * Ftp.java
 *
 * Version:
 *   $Id: $
 *
 * Revisions:
 *   $Log: $
 */

package org.lewk.ftp;

import org.lewk.ftp.*;

import java.net.Socket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;

import java.util.Scanner;
import java.util.NoSuchElementException;

/**
 * A basic implementation of the FTP client protocol (RFC959).
 *
 * @author Luke Macken (lewk@csh.rit.edu)
 */
public class Ftp
{
    public static final String prompt = ">> ";

    public static final String line_sep =
            System.getProperties().getProperty("line.separator");

    public static final String commands[] = {
        "ascii", "binary", "cd", "cdup", "debug", "dir", "get", "help",
        "passive", "put", "pwd", "quit", "user", "pass", "pasv", "list"
    };

    public static final int ASCII = 0;
    public static final int BINARY = 1;
    public static final int CD = 2;
    public static final int CDUP = 3;
    public static final int DEBUG = 4;
    public static final int DIR = 5;
    public static final int GET = 6;
    public static final int HELP = 7;
    public static final int PASSIVE = 8;
    public static final int PUT = 9;
    public static final int PWD = 10;
    public static final int QUIT = 11;
    public static final int USER = 12;
    public static final int PASS = 13;
    public static final int PASV = 14;
    public static final int LIST = 15;

    public static final String[] HELP_MESSAGE = {
        "ascii        --> Set ASCII transfer type",
        "binary       --> Set binary transfer type",
        "cd <path>    --> Change the remote working directory",
        "cdup         --> Change the remote working directory to the",
        "                 parent directory (i.e., cd ..)",
        "debug        --> Toggle debug mode",
        "dir          --> List the contents of the remote directory",
        "get <path>   --> Get a remote file",
        "help         --> Displays this text",
        "passive      --> Toggle passive/active mode",
        "put <path>   --> Transfer the specified file to the server",
        "pwd          --> Print the working directory on the server",
        "quit         --> Close the connection to the server and terminate",
        "user <login> --> Specify the user name (will prompt for password)"
    };

    /* The IP/hostname of the FTP server we are connecting to */
    private String serverAddr;

    /* Debugging mode */
    private boolean debug;

    /* ASCII transfer mode */
    private boolean ascii = true;

    /* If we are in passive or active mode */
    private boolean passive = true;

    /* I/O communication objects */
    private Socket sock;
    private Socket pasvSock = null;
    private ServerSocket activeSock = null;
    private BufferedReader pasvIn;
    private BufferedReader in;
    private PrintWriter out;

    /**
     * Default constructor.
     * Create a new FTP client and connect to a specified server.
     *
     * @param server Address of an FTP server
     */
    public Ftp (String server)
    {
        serverAddr = server;
        debug = true;

        try {
            sock = new Socket(server, 21);
            System.out.println("Connected to " + server);

            out = new PrintWriter(sock.getOutputStream(), true);
            in = new BufferedReader(
                    new InputStreamReader(sock.getInputStream()));

            System.out.println(getResponse());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Toggle debugging mode.
     */
    public void toggleDebug ()
    {
        if (debug) {
            debug("Debugging output off");
            debug = false;
        } else {
            debug = true;
            debug("Debugging output on");
        }
    }

    /**
     * Close the client connect to the server.
     */
    public void close ()
    {
        try {
            debug("Closing connect to server");
            System.out.println("=> QUIT");
            out.println("QUIT");
            System.out.println(getResponse());
            out.close();
            in.close();
            sock.close();
            if (pasvSock != null && !pasvSock.isClosed()) {
                pasvIn.close();
                pasvSock.close();
            }
            if (activeSock != null && !activeSock.isClosed()) {
                activeSock.close();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Receive a single line response from the server.
     *
     * @return the response from the server
     */
    private String getResponse()
    {
        try {
            return in.readLine();
        } catch (IOException e) {
            System.err.println("Error reading server response: " +
                    e.getMessage());
        }

        return null;
    }

    /**
     * Log a debugging message to the console if the user has specified
     * that debug mode should be enabled.  If it is not, then this method
     * is essentially a nop.
     *
     * @param msg the debugging message to log
     */
    private void debug (String msg)
    {
        if (debug)
            System.err.println(msg);
    }

    /**
     * Send a given username to the server.
     *
     * @param username the username to login with
     */
    public void user (String username)
    {
        out.println(commands[USER] + " " +  username);
        System.out.println(getResponse());
    }

    /**
     * Send a given password to the server.
     *
     * @param password the password for the user.
     */
    public void pass (String password)
    {
        out.println(commands[PASS] + " " + password);
        System.out.println(getResponse());
    }

    /**
     * Request a directory listing from the server.
     */
    public void dir ()
    {
        String userInput;
        BufferedReader srvIn;
        Socket srv = null;

        /* Setup the appropriate sockets */
        setupTransferMode();

        out.println(commands[LIST]);
        System.out.println("=> LIST");
        System.out.println(getResponse());

        try {
            /* Setup the proper input streams based on the transfer mode */
            if (passive) {
                srvIn = pasvIn;
            } else {
                srv = activeSock.accept();
                srvIn = new BufferedReader(
                        new InputStreamReader(srv.getInputStream()));
            }

            /* Display the directory listing */
            while ((userInput = srvIn.readLine()) != null) {
                System.out.println(userInput);
            }

            srvIn.close();
            if (srv != null) {
                srv.close();
            }
        } catch (IOException e) {
            System.err.println("Error recieving data from server: " +
                    e.getMessage());
        }

        /* Clean up */
        closeTransferMode();
        System.out.println(getResponse());
    }

    /**
     * Set the transfer type to ASCII.
     */
    public void ascii ()
    {
        ascii = true;
        System.out.println("=> TYPE A");
        out.println("TYPE A");
        System.out.println(getResponse());
    }

    /**
     * Toggle passive/active modes.
     */
    public void pasv ()
    {
        passive = (passive) ? false : true;
        System.out.println(((passive)?"Passive":"Active") + " mode enabled");
    }

    /**
     * Close any sockets associated with the current transfer mode.
     */
    private void closeTransferMode()
    {
        try {
            if (passive) {
                if (pasvSock != null && !pasvSock.isClosed()) {
                    debug("Closing passive connection");
                    pasvIn.close();
                    pasvSock.close();
                }
            } else {
                if (activeSock != null && !activeSock.isClosed()) {
                    debug("Closing active connection");
                    activeSock.close();
                }
            }
        } catch (IOException e) {
            System.err.println("Error closing transfer mode: " +
                    e.getMessage());
        }
    }

    /**
     * Setup the sockets needed for the given transfer mode (either passive
     * or active).
     */
    private void setupTransferMode()
    {
        String resp = "";

        if (passive) {
            debug("Switching to passive mode");
            System.out.println("=> PASV");
            out.println(commands[PASV]);
            resp = getResponse();
            System.out.println(resp);

            /* If we've successfully entered passive mode, setup the socket */
            if (resp.startsWith("227")) {
                String[] foo = resp.split(",");
                int pasvPort = new Integer(foo[4]) * 256 +
                    new Integer(foo[5].replaceAll("[a-zA-Z).]+", ""));

                debug("Opening passive socket on "+serverAddr+":"+pasvPort);

                try {
                    pasvSock = new Socket(serverAddr, pasvPort, null,
                            sock.getLocalPort() + 1);
                    pasvIn = new BufferedReader(
                            new InputStreamReader(pasvSock.getInputStream()));
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            } else {
                debug("Got invalid response from PASV command");
            }
        } else { /* Active mode */
            debug("Switching to active mode");

            try { 
                activeSock = new ServerSocket(0);
            } catch (IOException e) {
                System.err.println("Error creating active socket: " +
                        e.getMessage());
            }

            byte[] addr = sock.getLocalAddress().getAddress();
            debug("Listening on local port " + activeSock.getLocalPort());

            String portCmd = "PORT " + ((int)addr[0] & 0xFF) + "," +
                    ((int)addr[1] & 0xFF) + "," + ((int)addr[2] & 0xFF) +
                    "," + ((int)addr[3] & 0xFF) + "," +
                    activeSock.getLocalPort() / 256 + "," +
                    activeSock.getLocalPort() % 256;

            System.out.println("=> " + portCmd);
            out.println(portCmd);
            System.out.println(getResponse());
        }
    }

    /**
     * Print out the current working directory on the server.
     */
    public void pwd ()
    {
        System.out.println("=> PWD");
        out.println("PWD");
        System.out.println(getResponse());
    }

    /**
     * Set the transfer type to binary.
     */
    public void binary ()
    {
        ascii = false;
        System.out.println("=> TYPE I");
        out.println("TYPE I");
        System.out.println(getResponse());
    }

    /**
     * Tell the server to change working directories.
     *
     * @param dir the directory to change to.  if dir is '..', then this 
     *            function will call cdup() instead.
     */
    public void cd (String dir)
    {
        System.out.println("=> CWD " + dir);
        out.println("CWD " + dir);
        System.out.println(getResponse());
    }

    /**
     * Tell the server to go up a directory.
     */
    public void cdup ()
    {
        System.out.println("=> CDUP");
        out.println("CDUP");
        System.out.println(getResponse());
    }

    /**
     * Request a specified file from the server.
     *
     * @param path The path to the file to acquire
     */
    public void get (String path)
    {
        String userInput, resp;
        BufferedReader srvIn;
        InputStream inStr;
        Socket srv = null;

        /* Initialize the appropriate transfer mode */
        setupTransferMode();

        /* Request the file */
        System.out.println("=> RETR " + path);
        out.println("RETR " + path);

        /* Spit out the response */
        resp = getResponse();
        System.out.println(resp);

        /* Validate response */
        if (!resp.startsWith("150")) {
            debug("Invalid response from RETR command; aborting transfer");
            closeTransferMode();
            return;
        }

        try {
            /* Point to the appropriate input streams based on transfer mode */
            if (passive) {
                srvIn = pasvIn;
                inStr = pasvSock.getInputStream();
            } else {
                srv = activeSock.accept();
                inStr = srv.getInputStream();
            }

            /* ASCII transfer */
            if (ascii) {
                FileWriter outFile = new FileWriter(new File(path).getName());
                srvIn = new BufferedReader(new InputStreamReader(inStr));

                while ((userInput = srvIn.readLine()) != null) {
                    outFile.write(userInput + line_sep);
                }

                outFile.close();
                srvIn.close();
            } else { /* Binary transfer */
                int bytesRead = 0;
                byte[] buf = new byte[512];

                FileOutputStream outFile = new FileOutputStream(
                        new File(path).getName());
                BufferedInputStream binIn = new BufferedInputStream(inStr);

                while ((bytesRead = binIn.read(buf, 0, 512)) != -1) {
                    outFile.write(buf, 0, bytesRead);
                }

                outFile.close();
                binIn.close();
            }

            if (srv != null) {
                srv.close();
            }
        } catch (IOException e) {
            System.err.println("Error acquiring file: " + e.getMessage());
        }

        /* Clean up */
        closeTransferMode();
        System.out.println(getResponse());
    }

    /**
     * Main method which is in charge of parsing and interpreting user 
     * input and interacting with the FTP client.
     *
     * @param args Command line arguments; should only contain the server
     */
    public static void main (String args[])
    {
        boolean eof = false;
        String input = null;
        Scanner in = new Scanner(System.in);

        if (args.length != 1) {
            System.err.println("Usage: java Ftp <server>");
            System.exit(1);
        }

        /* Create a new FTP client */
        Ftp client = new Ftp(args[0]);

        do {
            try {
                System.out.print(prompt);
                input = in.nextLine();
            } catch (NoSuchElementException e) {
                eof = true;
            }

            /* Keep going if we have not hit end of file */
            if (!eof && input.length() > 0) {
                int cmd = -1;
                String argv[] = input.split("\\s+");

                for (int i = 0; i < commands.length && cmd == -1; i++)
                    if (commands[i].equalsIgnoreCase(argv[0]))
                        cmd = i;

                /* Execute the command */
                switch (cmd) {
                case ASCII:
                    client.ascii();
                    break;
                case BINARY:
                    client.binary();
                    break;
                case CD:
                    client.cd(argv[1]);
                    break;
                case CDUP:
                    client.cdup();
                    break;
                case DEBUG:
                    client.toggleDebug();
                    break;
                case DIR:
                    client.dir();
                    break;
                case GET:
                    client.get(argv[1]);
                    break;
                case HELP:
                    for (int i = 0; i < HELP_MESSAGE.length; i++)
                        System.out.println(HELP_MESSAGE[i]);
                    break;
                case PASSIVE:
                    client.pasv();
                    break;
                case PWD:
                    client.pwd();
                    break;
                case QUIT:
                    eof = true;
                    break;
                case USER:
                    client.user(argv[1]);
                    String password = PasswordField.getPassword("Password:  ");
                    client.pass(password);
                    break;
                default:
                    System.out.println("Invalid command");
                }
            }
        } while (!eof);

        /* Clean up */
        client.close();
    }
}
