/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.strcuts;

import javax.jms.DeliveryMode;
import javax.jms.Session;
import java.util.Hashtable;

/**
 * Title:        ConnectionParams
 * Description:  This is the class holds attributes for connecting and sending ENM messages.
 * @author A. Kevin Bailey
 * @version 0.5
 */
@SuppressWarnings({"WeakerAccess", "CanBeFinal", "unused"})
public class ConnectionParams
{
    public String username;
    public String password;
    public String emsURL;
    public String clientID;
    public String reqDest;
    public String resDest;
    public String logFileName;
    public String configFileName;
    public boolean isSSL;
    public boolean isSslJndi;
    public boolean isEcho;
    public boolean isFileAppend;
    public boolean isEmsInfoLogging;
    public int sizeOfMsg;
    public int latencyThreshold;
    public int deliveryMode;
    public int ackMode;
    public int stopAfter; // Number of requests
    public int delay; // Delay in msec.
    public long maxLogSize;
    public Hashtable envSSL; // Holds SSL settings, etc.


    public ConnectionParams()
    {
        username = null;
        password = null;
        emsURL = null;
        clientID = null;
        reqDest = null;
        resDest = null;
        configFileName = null;
        isSSL = false;
        isSslJndi = false;
        isEcho = false;
        isFileAppend = false;
        isEmsInfoLogging = false;
        sizeOfMsg = 0;
        latencyThreshold = 0;
        deliveryMode = DeliveryMode.NON_PERSISTENT;
        ackMode = Session.AUTO_ACKNOWLEDGE;
        stopAfter = 0;
        delay = 0;
        maxLogSize = 0;
        envSSL = null;
    }

   public String getAckMode()
   {
        String strName = "";

       if (ackMode == Session.AUTO_ACKNOWLEDGE) strName = "AUTO_ACKNOWLEDGE";
       else if (ackMode == Session.CLIENT_ACKNOWLEDGE) strName = "CLIENT_ACKNOWLEDGE";
       else if (ackMode == Session.DUPS_OK_ACKNOWLEDGE) strName = "DUPS_OK_ACKNOWLEDGE";
       else if (ackMode == com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_ACKNOWLEDGE) strName = "EXPLICIT_CLIENT_ACKNOWLEDGE";
       else if (ackMode == com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_DUPS_OK_ACKNOWLEDGE) strName = "EXPLICIT_CLIENT_DUPS_OK_ACKNOWLEDGE";
       else if (ackMode == com.tibco.tibjms.Tibjms.NO_ACKNOWLEDGE) strName = "NO_ACKNOWLEDGE";

       return strName;
   }
    
   public String getDeliveryMode()
   {
       String strName = "";

       if (deliveryMode == DeliveryMode.NON_PERSISTENT) strName = "NON_PERSISTENT";
       else if (deliveryMode == DeliveryMode.PERSISTENT) strName = "PERSISTENT";
       else if (deliveryMode == com.tibco.tibjms.Tibjms.RELIABLE_DELIVERY) strName = "RELIABLE_DELIVERY";

       return strName;
   }
}
