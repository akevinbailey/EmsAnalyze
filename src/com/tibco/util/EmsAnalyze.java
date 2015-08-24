/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util;

import java.util.*;
import javax.jms.*;

import com.tibco.tibjms.Tibjms;
import com.tibco.tibjms.TibjmsDeliveryMode;
import com.tibco.tibjms.TibjmsSSL;
import com.tibco.tibjms.naming.TibjmsContext;
import com.tibco.util.helpers.EmsAnalyzeClose;
import com.tibco.util.threads.RequestThread;
import com.tibco.util.threads.ResponseThread;
import com.tibco.util.strcuts.ConnectionParams;

/**
 * Title:        EmsAnalyze
 * Description:  This is the main class for the EmsAnalyze command line application.
 * @author A. Kevin Bailey
 * @version 0.7
 */
@SuppressWarnings({"FieldCanBeLocal", "unused", "CanBeFinal"})
public final class EmsAnalyze implements Runnable
{
    public final static String APP_NAME = "EmsAnalyze";
    public final static String APP_VERSION = "0.7";
    public final static String APP_DATE = "2012-10-19";

    public final static String APP_COMPANY = "TIBCO Software Inc.";
    public final static String APP_AUTHOR = "A. Kevin Bailey";
    public final static String APP_AUTHOR_EMAIL = "abailey@tibco.com";

    public final static String LOG_PREFIX = "emsAnalyse";

    public enum AppOperation { REQUEST_RESPONSE, REQUEST_ONLY, RESPONSE_ONLY}

    private ConnectionParams _connParams;
    private String _strConfigFileName;
    private int _intRequestThreads;
    private int _intResponseThreads;
    private AppOperation _appOperation = AppOperation.REQUEST_RESPONSE;


    public static void main(String args[])
    {
        new EmsAnalyze(args);
    }

    private EmsAnalyze(String args [])
    {
        _connParams = new ConnectionParams();

        // Default values
        _connParams.ackMode = Session.AUTO_ACKNOWLEDGE;
        _connParams.deliveryMode = TibjmsDeliveryMode.NON_PERSISTENT;
        _connParams.logFileName = LOG_PREFIX;
        _intRequestThreads = 1;
        _intResponseThreads = 1;

        parseArgs(args);
        run();
    }

    @SuppressWarnings("unchecked")
    private void parseArgs(String args [])
    {
        Vector ssl_trusted = new Vector();

        if (args.length == 0 || args[0].charAt(0) != '-') {
            System.err.println();
            System.err.println(" -------------------------------------------");
            System.err.println(" " + APP_NAME + " " + APP_VERSION);
            System.err.println();
            System.err.println(" No arguments specified (use -? for options)");
            System.err.println(" -------------------------------------------");
            System.err.println();
            System.exit(1);
        }

        try {
            // read arguments
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("-configFile") && args[i+1].charAt(0) != '-') {
                    _strConfigFileName = args[i+1];
                    break;
                }
                else if (args[i].equalsIgnoreCase("-connect") && args[i+1].charAt(0) != '-') {
                    _connParams.emsURL = args[i+1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-user") && args[i+1].charAt(0) != '-') {
                    _connParams.username = args[i+1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-password") && args[i+1].charAt(0) != '-') {
                    _connParams.password = args[i+1];
                    i++;
                }

                // SSL settings
                else if (args[i].equalsIgnoreCase("-ssl") || args[i].equalsIgnoreCase("-sslAuthOnly") || args[i].equalsIgnoreCase("-sslJndi")) {
                    _connParams.isSSL = true;
                    // Set the initial SSL config

                    _connParams.envSSL = new Hashtable(12);
                    if (!_connParams.envSSL.containsKey(TibjmsSSL.TRACE))
                        _connParams.envSSL.put(TibjmsSSL.TRACE, Boolean.FALSE);
                    if (!_connParams.envSSL.containsKey(TibjmsSSL.DEBUG_TRACE))
                        _connParams.envSSL.put(TibjmsSSL.DEBUG_TRACE, Boolean.FALSE);
                    if (!_connParams.envSSL.containsKey(TibjmsSSL.ENABLE_VERIFY_HOST))
                        _connParams.envSSL.put(TibjmsSSL.ENABLE_VERIFY_HOST, Boolean.FALSE);
                    if (!_connParams.envSSL.containsKey(TibjmsSSL.ENABLE_VERIFY_HOST_NAME))
                        _connParams.envSSL.put(TibjmsSSL.ENABLE_VERIFY_HOST_NAME, Boolean.FALSE);
                    if (!_connParams.envSSL.containsKey(TibjmsSSL.AUTH_ONLY))
                        _connParams.envSSL.put(TibjmsSSL.AUTH_ONLY, Boolean.FALSE);
                    if (!_connParams.envSSL.containsKey(TibjmsSSL.VENDOR))
                        _connParams.envSSL.put(TibjmsSSL.VENDOR, "j2se-default");

                    if (args[i].equalsIgnoreCase("-sslAuthOnly"))
                        _connParams.envSSL.put(TibjmsSSL.AUTH_ONLY, Boolean.TRUE);

                    if (args[i].equalsIgnoreCase("-sslJndi")) {
                        // Specify SSL as the security protocol to use by the Initial Context
                        _connParams.isSslJndi = true;
                        _connParams.envSSL.put(TibjmsContext.SECURITY_PROTOCOL, "ssl");
                    }

                }
                else if (args[i].equalsIgnoreCase("-sslVendor") && args[i+1].charAt(0) != '-') {
                    _connParams.envSSL.put(TibjmsSSL.VENDOR, args[i + 1]);
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-sslCiphers") && args[i+1].charAt(0) != '-') {
                    _connParams.envSSL.put(TibjmsSSL.CIPHER_SUITES, args[i + 1]);
                    i++;
                }
                // Set trace for client-side operations, loading of certificates and other
                else if (args[i].equalsIgnoreCase("-sslTrace")) {
                    _connParams.envSSL.put(TibjmsSSL.TRACE, Boolean.TRUE);
                    //com.tibco.tibjms.TibjmsSSL.setClientTracer(System.out);
                }
                // Set vendor trace. Has no effect for "j2se", "entrust61" uses
                // This to trace SSL handshake
                else if (args[i].equalsIgnoreCase("-sslDebugTrace")) {
                    _connParams.envSSL.put(TibjmsSSL.DEBUG_TRACE, Boolean.TRUE);
                }
                // Set trusted certificates if specified
                else if (args[i].equalsIgnoreCase("-sslTrusted")) {
                    ssl_trusted.clear();
                    while (i+1 < args.length && !args[i+1].startsWith(("-"))) {
                        i++;
                        //TibjmsSSL.addTrustedCerts(args[i]);
                        ssl_trusted.add(args[i]);
                    }
                    _connParams.envSSL.put(TibjmsSSL.TRUSTED_CERTIFICATES, ssl_trusted);
                }
                // Set trusted certificates if specified
                else if (args[i].equalsIgnoreCase("-sslHostname") && args[i+1].charAt(0) != '-') {
                    _connParams.envSSL.put(TibjmsSSL.EXPECTED_HOST_NAME, args[i + 1]);
                    i++;
                }
                // Set client identity if specified. ssl_key may be null
                // if identity is PKCS12, JKS or EPF. 'j2se' only supports
                // PKCS12 and JKS. 'entrust61' also supports PEM and PKCS8.
                else if (args[i].equalsIgnoreCase("-sslIdentity") && args[i+1].charAt(0) != '-') {
                    _connParams.envSSL.put(TibjmsSSL.IDENTITY, args[i + 1]);
                }
                else if (args[i].equalsIgnoreCase("-sslPassword") && args[i+1].charAt(0) != '-') {
                    _connParams.envSSL.put(TibjmsSSL.PASSWORD, args[i + 1]);
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-sslKey") && args[i+1].charAt(0) != '-') {
                    _connParams.envSSL.put(TibjmsSSL.PRIVATE_KEY, args[i + 1]);
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-verifyHostName")) {
                    _connParams.envSSL.put(TibjmsSSL.ENABLE_VERIFY_HOST_NAME, Boolean.TRUE);
                }
                else if (args[i].equalsIgnoreCase("-verifyHost")) {
                    _connParams.envSSL.put(TibjmsSSL.ENABLE_VERIFY_HOST, Boolean.TRUE);
                }
                else if (args[i].equalsIgnoreCase("-logFile") && args[i+1].charAt(0) != '-') {
                    _connParams.logFileName = args[i+1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-clientId") && args[i+1].charAt(0) != '-') {
                    _connParams.clientID = args[i+1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-reqDest") && args[i+1].charAt(0) != '-') {
                    _connParams.reqDest = args[i+1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-resDest") && args[i+1].charAt(0) != '-') {
                    _connParams.resDest = args[i+1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-reqThreads") && args[i+1].charAt(0) != '-') {
                    _intRequestThreads = Integer.parseInt(args[i+1]);
                    if (_intRequestThreads < 100)
                        i++;
                    else {
                        System.out.println("\nError:  -reqThread must be less than 100.");
                        System.exit(0);
                    }
                }
                else if (args[i].equalsIgnoreCase("-repThreads") && args[i+1].charAt(0) != '-') {
                    _intResponseThreads = Integer.parseInt(args[i+1]);
                    if (_intResponseThreads < 100)
                        i++;
                    else {
                        System.out.println("\nError:  -repThreads must be less than 100.");
                        System.exit(0);
                    }
                }
                else if (args[i].equalsIgnoreCase("-operationMode")  && args[i+1].charAt(0) != '-') {
                    if (args[i+1].equalsIgnoreCase("REQUEST_RESPONSE")) _appOperation = AppOperation.REQUEST_RESPONSE;
                    else if (args[i+1].equalsIgnoreCase("REQUEST_ONLY")) _appOperation = AppOperation.REQUEST_ONLY;
                    else if (args[i+1].equalsIgnoreCase("RESPONSE_ONLY")) _appOperation = AppOperation.RESPONSE_ONLY;
                    else {
                        System.err.println("ERROR: -deliveryMode can only be \"NORMAL\", \"REQUEST_ONLY\", or \"RESPONSE_ONLY\".");
                        System.exit(1);
                    }
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-deliveryMode")  && args[i+1].charAt(0) != '-') {
                    if (args[i+1].equalsIgnoreCase("NON_PERSISTENT")) _connParams.deliveryMode = TibjmsDeliveryMode.NON_PERSISTENT;
                    else if (args[i+1].equalsIgnoreCase("PERSISTENT")) _connParams.deliveryMode = TibjmsDeliveryMode.PERSISTENT;
                    else if (args[i+1].equalsIgnoreCase("RELIABLE")) _connParams.deliveryMode = TibjmsDeliveryMode.RELIABLE;
                    else {
                        System.err.println("ERROR: -deliveryMode can only be \"NON_PERSISTENT\", \"PERSISTENT\", or \"RELIABLE\".");
                        System.exit(1);
                    }
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-ackMode")  && args[i+1].charAt(0) != '-') {
                    if (args[i+1].equalsIgnoreCase("AUTO_ACKNOWLEDGE")) _connParams.ackMode = Session.AUTO_ACKNOWLEDGE;
                    else if (args[i+1].equalsIgnoreCase("CLIENT_ACKNOWLEDGE")) _connParams.ackMode = Session.CLIENT_ACKNOWLEDGE;
                    else if (args[i+1].equalsIgnoreCase("DUPS_OK_ACKNOWLEDGE")) _connParams.ackMode = Session.DUPS_OK_ACKNOWLEDGE;
                    else if (args[i+1].equalsIgnoreCase("EXPLICIT_CLIENT_ACKNOWLEDGE")) _connParams.ackMode = Tibjms.EXPLICIT_CLIENT_ACKNOWLEDGE;
                    else if (args[i+1].equalsIgnoreCase("NO_ACKNOWLEDGE")) _connParams.ackMode = Tibjms.NO_ACKNOWLEDGE;
                    else {
                        System.err.println("ERROR: -ackMode can only be \"AUTO_ACKNOWLEDGE\", \"CLIENT_ACKNOWLEDGE\"," +
                                " \"DUPS_OK_ACKNOWLEDGE\", \"EXPLICIT_CLIENT_ACKNOWLEDGE\", or \"NO_ACKNOWLEDGE\".");
                        System.exit(1);
                    }
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-msgSize") && args[i+1].charAt(0) != '-') {
                    _connParams.sizeOfMsg = Integer.parseInt(args[i+1]);
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-logOnLatency") && args[i+1].charAt(0) != '-') {
                    _connParams.latencyThreshold = Integer.parseInt(args[i+1]);
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-echo")) {
                    _connParams.isEcho = true;
                }
                else if (args[i].equalsIgnoreCase("-noLogFile")) {
                    _connParams.logFileName = null;
                }
                else if (args[i].equalsIgnoreCase("-logEmsInfo")) {
                    _connParams.isEmsInfoLogging = true;
                }
                else if (args[i].equalsIgnoreCase("-version")) {
                    System.out.println(APP_NAME);
                    System.out.println("Version:    " + APP_VERSION);
                    System.out.println("Build Date: " + APP_DATE);
                    System.exit(0);
                }
                else if (args[i].equalsIgnoreCase("-author")) {
                    System.out.println("Company: " + APP_COMPANY);
                    System.out.println("Author:  " + APP_AUTHOR);
                    System.out.println("Email:   " + APP_AUTHOR_EMAIL);
                    System.exit(0);
                }
                else if (args[i].equalsIgnoreCase("-examples")) {
                    displayExamples();
                    System.exit(0);
                }
                else if (args[i].equalsIgnoreCase("-fileAppend")) {
                    _connParams.isFileAppend = true;
                }
                else if (args[i].equalsIgnoreCase("-delay") && args[i+1].charAt(0) != '-') {
                    _connParams.delay = Integer.parseInt(args[i+1]);
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-help") || args[i].equalsIgnoreCase("-?")) {
                    usage(); System.exit(0);
                }
                else if (args[i].equalsIgnoreCase("-license")) {
                    System.out.println(
                            "\n This sample application is free and not supported by TIBCO or its affiliates." +
                                    "\n The embedded EMS client libraries are governed by the TIBCO EMS license and are" +
                                    "\n only usable by individuals or organizations with a valid TIBCO EMS license" +
                                    "\n agreement. Please see the aforementioned license agreement for further details.");
                    System.exit(0);
                }
                else if (args[i].equalsIgnoreCase("-stopAfter") && args[i+1].charAt(0) != '-') {
                    _connParams.stopAfter = Integer.parseInt(args[i+1]);
                    i++;
                }
                else if (args[i].charAt(0) == '-') {  // used to prevent misspelling and errors
                    System.err.println(" " + APP_NAME + " " + APP_VERSION);
                    System.err.println();
                    System.err.println(" ERROR: Unexpected argument near: " + args[i]);
                    System.err.println(" (use -? for options for the appropriate arguments)");
                    System.err.println();
                    System.exit(1);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * The usage method prints the application help to the screen.
     */
    public static void usage()
    {
        System.out.println(" Usage of EmsAnalyze:  (The <> brackets mean a mandatory entry.  The [] brackets mean an optional entry.)");

        System.out.println();
        System.out.println(" java -jar EmsAnalyze.jar -configfile <file-name>");
        System.out.println(" or");
        System.out.println(" java -jar EmsAnalyze.jar -connect <ems-url> -reqDest <queue-name> -msgSize <size-in-bytes> -delay <msec> -logFile <file-name> -[options]");

        System.out.println();
        System.out.println(" ---- Configuration File ----");
        System.out.println("   -configFile <file-name>   : Read the configuration from the EmsAnalyze configuration file.");
        System.out.println("                                (No other args are necessary.)");
        System.out.println();
        System.out.println(" ---- Connection Parameters ----");
        System.out.println("   -connect <url>             : Use a connection factory (default: QueueConnectionFactory for queues,");
        System.out.println("   -user <name>               : OPTIONAL - User name used for EMS.");
        System.out.println("   -password <string>         : OPTIONAL - Password used for EMS.");
        System.out.println("   -clientid <name>           : OPTIONAL - JMS client ID.");
        System.out.println("   -operationalMode < REQUEST_RESPONSE  : OPTIONAL - EmsAnalyze will run the request and response threads. (Default)");
        System.out.println("                    | REQUEST_ONLY      : EmsAnalyze will only run the request threads.");
        System.out.println("                    | RESPONSE_ONLY >   : EmsAnalyze will only run the response threads.");

        // set client identity if specified. ssl_key may be null if identity is PKCS12, JKS or EPF.
        // 'j2se' only supports PKCS12 and JKS. 'entrust61' also supports PEM and PKCS8.
        System.out.println();
        System.out.println(" ---- SSL Options ----");
        System.out.println("   -ssl                        : Start SSL transport. (required for all SSL communication)");
        System.out.println("   OpenSSL Options (OPTIONAL)  :");
        System.out.println("      -sslJndi                 : Use SSL for JNDI lookup.");
        System.out.println("      -sslAuthOnly             : Use SSL encryption for authentication only.");
        System.out.println("      -sslVendor <name>        : SSL vendor: 'j2se-default', 'j2se', 'entrust61', or 'ibm'.");
        System.out.println("      -sslCiphers <name>       : OpenSSL names for the cipher suites used for encryption.");
        System.out.println("      -sslTrace                : Trace SSL initialization.");
        System.out.println("      -sslDebugTrace           : Trace SSL handshake and related.");
        System.out.println("      -sslTrusted <file1 ...>  : File(s) with trusted certificate(s).");
        System.out.println("      -sslHostname <host-name> : Name expected in the server certificate.");
        System.out.println("      -sslIdentity <file-name> : Client identity file.");
        System.out.println("      -sslPassword <string>    : Password for the client identity file.");
        System.out.println("      -sslKey <file-name>      : Client key file or private key file.");
        System.out.println("                               : (only valid for 'entrust' and 'ibm')");
        System.out.println("      -verifyHostName          : Host name verification.");
        System.out.println("      -verifyHost              : Host verification.");
        System.out.println();
        System.out.println(" ---- Logging Parameters ----");
        System.out.println("   -delay <msec>         : Perform request/response latency test every <msec> milliseconds.");
        System.out.println("   -logFile <name>       : OPTIONAL - Change the log file URI to <name>. (default is 'emsAnalyse.log')");
        System.out.println("   -fileappend           : OPTIONAL - Append output to existing log files. (default is to create a new file)");
        System.out.println("   -noLogFile            : OPTIONAL - Stops the automatic file logging.");
        System.out.println("   -echo                 : OPTIONAL - Print the request/threads times and option server info to the screen.");
        System.out.println("   -logEmsInfo           : OPTIONAL - Also Gather EMS server statistics. Requires Admin privileges.");
        System.out.println("                         : (EMS stat info will only output to the log file of the first requester thread.)");
        System.out.println("   -logOnLatency <msec>  : OPTIONAL - Log only when the request/response latency is greater than <msec>.");
        System.out.println();
        System.out.println(" ---- JMS Message Parameters ----");
        System.out.println("   -msgSize <size-in-bytes>  : Generates request messages of this size.");
        System.out.println("   -reqDest <destination>    : Send messages to the request queue <destination>.");
        System.out.println("   -resDest <destination>    : OPTIONAL - The static threads queue. If given, EmsAnalyze will send");
        System.out.println("                               threads messages to this queue and use the a message selector on");
        System.out.println("                               JMSCorrelationID property. If not present EmsAnalyze will ues temp queues.");
        System.out.println("   -ackMode < AUTO_ACKNOWLEDGE            : OPTIONAL - default acknowledge mode");
        System.out.println("            | CLIENT_ACKNOWLEDGE          : standard behavior");
        System.out.println("            | DUPS_OK_ACKNOWLEDGE         : standard behavior");
        System.out.println("            | EXPLICIT_CLIENT_ACKNOWLEDGE : com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_ACKNOWLEDGE");
        System.out.println("            | NO_ACKNOWLEDGE >            : com.tibco.tibjms.Tibjms.NO_ACKNOWLEDGE");
        System.out.println("   -deliveryMode < NON_PERSISTENT  : OPTIONAL - Select the delivermode of the message. (for -resDest only)");
        System.out.println("                 | PERSISTENT");
        System.out.println("                 | RELIABLE >");
        System.out.println();
        System.out.println(" ---- Threading Parameters ----");
        System.out.println("   -reqThreads <int>         : OPTIONAL - Number of concurrent request threads. Default is 1");
        System.out.println("   -repThreads <int>         : OPTIONAL - Number of concurrent response threads. Default is 1");
        System.out.println("   -stopAfter <int>          : OPTIONAL - Stop logging and exits after <int> of request messages.");

        System.out.println();
        System.out.println(" ---- Help Option ----");
        System.out.println("   -help -?  : Usage information");
        System.out.println("   -author   : Contact information");
        System.out.println("   -examples : Displays command line examples");
        System.out.println("   -version  : Displays version information");
        System.out.println("   -license  : " + APP_NAME + " usage license (by using this software you agree to the license)");
    }

    void displayExamples()
    {
        System.out.println(" ---- Examples ----");
        System.out.println("   java -jar EmsAnalyze.jar -connect tcp://localhost:7222 -clientId Tester -reqDest my.request\n" +
                           "                            -resDest my.response -msgSize 1024 -echo");
        System.out.println("   java -jar EmsAnalyze.jar -connect tcp://localhost:7222 -clientId Tester -reqDest my.request\n" +
                           "                            -msgSize 1024");
        System.out.println("   java -jar EmsAnalyze.jar -connect tcp://localhost:7222 -clientId Tester -reqDest my.request\n" +
                           "                            -msgSize 10000 -delay 500 -stopAfter 10 -logFile C:\\emsanalyze.csv");
        System.out.println("   java -jar EmsAnalyze.jar -connect tcp://localhost:7222 -clientId Tester -reqDest my.request\n" +
                           "                            -msgSize 10000 -delay 500 -stopAfter 10 -echo -reqThreads 5 -repThreads 5\n" +
                           "                            -logFile C:\\emsanalyze.log");
        System.out.println("   java -jar EmsAnalyze.jar -connect tcp://localhost:7222 -user admin -password tibco123 -clientId Tester\n" +
                           "                            -reqDest my.request -msgSize 100000 -delay 500 -stopAfter 20 -echo -reqThreads 2\n" +
                           "                            -repThreads 2 -logEmsInfo -logFile C:\\emsanalyze");
        System.out.println("   java -jar EmsAnalyze.jar -ssl -connect ssl://localhost:7243 -user admin -password tibco123\n" +
                           "                            -clientId Tester -reqDest my.request -msgSize 1 -delay 1000 -reqThreads 3\n" +
                           "                            -repThreads 3 -stopAfter 10 -echo -logEmsInfo -logFile C:\\emsanalyze.csv");
        System.out.println("   java -jar EmsAnalyze.jar -sslAuthOnly -connect ssl://localhost:7243 -user admin -password tibco123\n" +
                           "                            -clientId Tester -reqDest my.request -msgSize 1 -delay 1000 -stopAfter 100\n" +
                           "                            -echo -logEmsInfo -logFile C:\\emsanalyze.csv");
    }

    public void run()
    {
        Thread[] tRequests = new Thread[_intRequestThreads];
        Thread[] tResponses = new Thread[_intResponseThreads];

        System.out.println(" " + EmsAnalyze.APP_NAME + " started");
        System.out.println(" ------------------- ");
        System.out.println(" Connection URL   :  " + _connParams.emsURL);
        System.out.println(" Using SSL        :  " + (_connParams.isSSL && _connParams.envSSL.containsKey(TibjmsSSL.AUTH_ONLY) && _connParams.envSSL.get(TibjmsSSL.AUTH_ONLY).equals(Boolean.TRUE) ? "Auth only" : _connParams.isSSL));
        System.out.println(" Client ID        :  " + _connParams.clientID);
        System.out.println(" Username         :  " + ((_connParams.username) == null ? "" : _connParams.username));
        System.out.println(" Delay            :  " + _connParams.delay + " msec");
        if (_appOperation != AppOperation.RESPONSE_ONLY) System.out.println(" Request Threads  :  " + _intRequestThreads);
        if (_appOperation != AppOperation.REQUEST_ONLY) System.out.println(" Response Threads :  " + _intResponseThreads);
        if (_connParams.stopAfter == 0) System.out.println(" Stop After       :  Continuous Loop");
        else System.out.println(" Stop After       :  " + _connParams.stopAfter + " requests");
        System.out.println(" Request Queue    :  " + _connParams.reqDest);
        if (_connParams.resDest == null || _connParams.resDest.equals("")) System.out.println(" Response Queue   :  Generated temp queue");
        else System.out.println(" Response Queue   :  " + _connParams.resDest);
        System.out.println(" Message Ack Mode :  " + _connParams.getAckMode());
        System.out.println(" Delivery Mode    :  " + _connParams.getDeliveryMode());
        System.out.println(" Message Size     :  " + _connParams.sizeOfMsg + " Bytes");
        System.out.println(" Operational Mode :  " + (_appOperation == AppOperation.REQUEST_RESPONSE ? "REQUEST and RESPONSE" : _appOperation.name()));
        if (_appOperation != AppOperation.RESPONSE_ONLY) {
            System.out.println(" Logging EMS Info :  " + _connParams.isEmsInfoLogging);
        }    
        // Create shutdown hook to close connections and files
        EmsAnalyzeClose shutdownThread = new EmsAnalyzeClose();
        Runtime.getRuntime().addShutdownHook(shutdownThread);
        
        // REQUEST_RESPONSE and RESPONSE_ONLY
        if (_appOperation != AppOperation.REQUEST_ONLY) {
            // Create a response threads.
            for (int i=0; i < _intResponseThreads; i++) {
                ResponseThread responseThread = new ResponseThread(_connParams, i);

                tResponses[i] = new Thread(responseThread);
                tResponses[i].start();

                // Add JMS Connection from the Response Thread to shutdown hook.
                shutdownThread.addJmsConnection(responseThread.getConnection());
            }
        }

        // REQUEST_RESPONSE and REQUEST_ONLY
        if (_appOperation != AppOperation.RESPONSE_ONLY) {
            if (_connParams.isEcho) {
                System.out.printf("\n %-25s %-38s %-38s %s\n", "Timestamp", "Request Message ID", "Response Message ID", "Response (msec)");
            }
            // Create a request thread.
            for (int i=0; i < _intRequestThreads; i++) {
                RequestThread requestThread = new RequestThread(_connParams, i);

                tRequests[i] = new Thread(requestThread);
                tRequests[i].start();

                // Add JMS Connection from the Response Thread to shutdown hook.
                shutdownThread.addJmsConnection(requestThread.getConnection());
                // Add File Stream from the Response Thread to shutdown hook.
                shutdownThread.addFileStream(requestThread.getFileStream());
            }
        }

        try {
            for (int i=0; i < _intRequestThreads; i++) {
                if (tRequests[i].isAlive())
                    tRequests[i].join();
            }
        }
        catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        
        stop();
    }

    public void stop()
    {
        System.exit(0);
    }

}