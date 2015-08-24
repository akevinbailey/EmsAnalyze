/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.threads;

import com.tibco.tibjms.TibjmsQueueConnectionFactory;
import com.tibco.tibjms.admin.DestinationInfo;
import com.tibco.tibjms.admin.ServerInfo;
import com.tibco.tibjms.admin.TibjmsAdmin;
import com.tibco.tibjms.admin.TibjmsAdminException;
import com.tibco.util.helpers.GenerateText;
import com.tibco.util.strcuts.ConnectionParams;
import javax.jms.*;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Title:        ResponseThread
 * Description:  This is the class creates a thread to respond to the EMS ping message from the EmsAnalyze app.
 * @author A. Kevin Bailey
 * @version 0.5
 */
@SuppressWarnings({"UnusedDeclaration", "CanBeFinal", "unused", "UnusedAssignment"})
public class RequestThread implements Runnable
{
    public static final String NEW_LINE = System.getProperty("line.separator");
    public static final float MEGABYTE = 1048576.0f;
    public static final float KILOBYTE = 1024.0f;

    private ConnectionParams _connReqParams;
    private QueueConnection _connReqQEms;
    private TibjmsAdmin _tibAdmin;
    private FileOutputStream _fioStream;
    protected String _strClientId;
    protected String _strFileName;
    private boolean _blnSend;
    private boolean _blnLogRotate;
    private boolean _isEmsInfoLogging;
    private int _intThreadId;
    private int _intLogNumber;
    private SimpleDateFormat _formatDate;

    public RequestThread(ConnectionParams connParams, int threadId)
    {
        _connReqParams = connParams;
        _intThreadId = threadId;
        _blnSend = true;
        _blnLogRotate = false;
        _intLogNumber = 0;
        _strClientId = _connReqParams.clientID == null ? null : _connReqParams.clientID + _intThreadId;
        _strFileName = _connReqParams.logFileName;
        _formatDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());

        // Only log EMS info for the first thread.
        _isEmsInfoLogging = _connReqParams.isEmsInfoLogging && _intThreadId == 0;
    }

    public void run()
    {
        TibjmsQueueConnectionFactory connQFactory;
        QueueSession sesQEms;
        QueueRequestor qRequester;
        QueueSender qSender;
        QueueReceiver qReceiver;
        Queue qReqDestination;
        Queue qResDestination;
        TextMessage msgReq;
        TextMessage msgRes;
        long intReqTime;
        long intResTime;
        long intMsgCount = 0;

        try {
            createLogFile();

            // Set up Queue Connection for requests
            connQFactory = new TibjmsQueueConnectionFactory(_connReqParams.emsURL, _strClientId, _connReqParams.envSSL);
            if (_connReqParams.username == null || _connReqParams.username.equals(""))
                _connReqQEms = connQFactory.createQueueConnection();
            else
                _connReqQEms = connQFactory.createQueueConnection(_connReqParams.username, _connReqParams.password);

            // Set up EMS Admin connection of EMS Server Info
            if (_isEmsInfoLogging)
                _tibAdmin = new TibjmsAdmin(_connReqParams.emsURL, _connReqParams.username, _connReqParams.password, _connReqParams.envSSL);

            sesQEms = _connReqQEms.createQueueSession(false, _connReqParams.ackMode);
            msgReq = sesQEms.createTextMessage(GenerateText.genChars('*', _connReqParams.sizeOfMsg));
            _connReqQEms.start();

            qReqDestination = sesQEms.createQueue(_connReqParams.reqDest);

            if (_connReqParams.resDest == null || _connReqParams.resDest.equals("")) { // Use a temp queue
                qRequester = new QueueRequestor(sesQEms, qReqDestination);
                while (_blnSend) {
                    intReqTime = System.currentTimeMillis();
                    msgRes = (TextMessage)qRequester.request(msgReq);
                    intResTime = System.currentTimeMillis();
                    logStats(msgReq.getJMSMessageID(), msgRes.getJMSMessageID(), intReqTime, intResTime);
                    if (_connReqParams.delay > 0) {
                        try {
                            Thread.sleep(_connReqParams.delay);
                        }
                        catch (InterruptedException ie) {
                            // do nothing
                        }
                    }
                    // Stop the loop if _blnSends is still true and the loop has exceeded the stop count.
                    _blnSend = _blnSend && (_connReqParams.stopAfter == 0 || ++intMsgCount < _connReqParams.stopAfter);
                }
            }
            else { // Use fixed queue with message selector
                String strSelector;
                qResDestination = sesQEms.createQueue(_connReqParams.resDest);
                qSender = sesQEms.createSender(qReqDestination);
                qSender.setDeliveryMode(_connReqParams.deliveryMode);
                msgReq.setJMSReplyTo(qResDestination);
                while (_blnSend) {
                    intReqTime = System.currentTimeMillis();
                    qSender.send(msgReq);
                    strSelector = "JMSCorrelationID = '" + msgReq.getJMSMessageID() + "'";
                    qReceiver = sesQEms.createReceiver(qResDestination, strSelector);
                    msgRes = (TextMessage)qReceiver.receive();
                    intResTime = System.currentTimeMillis();
                    logStats(msgReq.getJMSMessageID(), msgRes.getJMSMessageID(), intReqTime, intResTime);
                    if (_connReqParams.delay > 0) {
                        try {
                            Thread.sleep(_connReqParams.delay);
                        }
                        catch (InterruptedException ie) {
                            // do nothing
                        }
                    }
                    // Stop the loop if _blnSends is still true and the loop has exceeded the stop count.
                    _blnSend = _blnSend && (_connReqParams.stopAfter == 0 || ++intMsgCount < _connReqParams.stopAfter);
                }
            }
            _fioStream.close();
        }
        catch (JMSException jex){
            jex.printStackTrace();
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        catch (TibjmsAdminException tae) {
            tae.printStackTrace();
        }
    }

    private void createLogFile() {
        String strFileName;
        
        if (_connReqParams.logFileName != null) {
            // Add Thread Id and log number.  The files are CSV but sometimes user want them to end in .log, so preserve the .log ending.
            if (_blnLogRotate)
                if (_strFileName.endsWith(".log"))
                    _strFileName = _strFileName.substring(0, (_strFileName.length() - 11)) + String.format("_%02d-%03d.log", _intThreadId, _intLogNumber);
                else
                    _strFileName = _strFileName.substring(0, (_strFileName.length() - 11)) + String.format("_%02d-%03d.csv", _intThreadId, _intLogNumber);
            else if (_strFileName.endsWith(".log"))
                _strFileName = _strFileName.substring(0, _strFileName.lastIndexOf(".log")) + String.format("_%02d-%03d.log", _intThreadId, _intLogNumber);
            else if (_strFileName.endsWith(".csv"))
                _strFileName = _strFileName.substring(0, _strFileName.lastIndexOf(".csv")) + String.format("_%02d-%03d.csv", _intThreadId, _intLogNumber);
            else
                _strFileName = _strFileName + String.format("_%02d-%03d.csv", _intThreadId, _intLogNumber);

            try {
                _fioStream = new FileOutputStream(_strFileName, _connReqParams.isFileAppend);
                _blnLogRotate = true;
            }
            catch (FileNotFoundException fnf) {
                fnf.printStackTrace();
            }

            if (!_connReqParams.isFileAppend) {
                try {
                    _fioStream.write("Timestamp,Request Message ID,Response Message ID,Response (msec)".getBytes());
                    if (_isEmsInfoLogging) {
                        _fioStream.write((",Static Topics,Dynamic Topics" +
                                ",Temp Topics,Durables" +
                                ",Static Queues" +
                                ",Dynamic Queues" +
                                ",Temp Queues" +
                                ",Connections" +
                                ",Sessions" +
                                ",Producers" +
                                ",Consumers" +
                                ",Pending Messages" +
                                ",Pending Msg Size (MB)" +
                                ",Msg Memory Usage (MB)" +
                                ",Msg Memory Max (MB)" +
                                ",Msg Memory Pooled (MB)" +
                                ",Synchronous Storage (MB)" +
                                ",Asynchronous Storage (MB)" +
                                ",Inbound Msg Rate (msg/sec)" +
                                ",Inbound Data Rate (MB/sec)" +
                                ",Outbound Msg Rate (msg/sec)" +
                                ",Outbound Data Rate (MB/sec)" +
                                ",Disk Read Rate (MB/sec)" +
                                ",Disk Write Rate (MB/sec)").getBytes());
                    }
                    _fioStream.write(NEW_LINE.getBytes());
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }

    private void logStats(String strReqId, String strResId, long intReqTime, long intResTime)
    {
        ServerInfo serverInfo = null;
        long intLatency = intResTime - intReqTime;
        
        if (_connReqParams.latencyThreshold == 0 || intLatency > _connReqParams.latencyThreshold) {
            if (_isEmsInfoLogging) {
                try {
                    serverInfo = _tibAdmin.getInfo();
                }
                catch (TibjmsAdminException tae) {
                    tae.printStackTrace();
                }
            }

            if (_connReqParams.isEcho) {  //%-25s
                String strOut = String.format("%-64s %-38s %s", _formatDate.format(new Date()), strResId, intLatency); // TODO: Remove tabs and use spaces.
                if (_isEmsInfoLogging)
                    strOut += ("\t Logging EMS info to file.");
                System.out.println(" " + strOut);
            }

            if (_fioStream != null) {
                String strLine = _formatDate.format(new Date()) + "," + strReqId + "," + strResId + "," + intLatency;
                if (_isEmsInfoLogging && serverInfo != null) {
                    try {
                        float fSize;
                        strLine += ("," + _tibAdmin.getTopics(null, DestinationInfo.DEST_GET_STATIC).length); // Static Topics
                        strLine += ("," + _tibAdmin.getTopics(null, DestinationInfo.DEST_GET_DYNAMIC).length); // Dynamic Topics
                        strLine += ("," + _tibAdmin.getTopics("$TMP$.>", DestinationInfo.DEST_GET_ALL).length); // Temp Topics
                        strLine += ("," + serverInfo.getDurableCount()); // Durables
                        strLine += ("," + _tibAdmin.getQueues(null, DestinationInfo.DEST_GET_STATIC).length); // Static Queues
                        strLine += ("," + _tibAdmin.getQueues(null, DestinationInfo.DEST_GET_DYNAMIC).length); // Dynamic Queues
                        strLine += ("," + _tibAdmin.getQueues("$TMP$.>", DestinationInfo.DEST_GET_ALL).length); // Temp Queues
                        strLine += ("," + serverInfo.getConnectionCount()); // Connections
                        strLine += ("," + serverInfo.getSessionCount()); // Sessions
                        strLine += ("," + serverInfo.getProducerCount()); // Producers
                        strLine += ("," + serverInfo.getConsumerCount()); // Consumers
                        strLine += ("," + serverInfo.getPendingMessageCount()); // Pending Messages
                        fSize = serverInfo.getPendingMessageSize()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Pending Msg Size
                        fSize = serverInfo.getMsgMem()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Msg Memory Usage
                        fSize = serverInfo.getMaxMsgMemory()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Msg Memory Max
                        fSize = serverInfo.getMsgMemPooled()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Msg Memory Pooled
                        fSize = serverInfo.getSyncDBSize()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Synchronous Storage
                        fSize = serverInfo.getAsyncDBSize()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Asynchronous Storage
                        strLine += ("," + serverInfo.getInboundMessageRate()); //Inbound Msg Rate
                        fSize = serverInfo.getInboundBytesRate()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Inbound Data Rate
                        strLine += ("," + serverInfo.getOutboundMessageRate()); // Outbound Msg Rate
                        fSize = serverInfo.getOutboundBytesRate()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Outbound Data Rate
                        fSize = serverInfo.getDiskReadRate()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Disk Read Rate
                        fSize = serverInfo.getDiskWriteRate()/MEGABYTE; strLine += ("," + String.format("%.3f", fSize)); // Disk Write Rate
                    }
                    catch (TibjmsAdminException tae) {
                        tae.printStackTrace();
                    }
                }
                try {
                    _fioStream.write(strLine.getBytes());
                    _fioStream.write(NEW_LINE.getBytes());
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                // For log file rotation
                try {
                    if (_connReqParams.maxLogSize != 0 && _fioStream.getChannel().size() >= _connReqParams.maxLogSize) {
                        _intLogNumber++;
                        _fioStream.close();
                        createLogFile();
                    }
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                catch (NullPointerException npe) {
                    npe.printStackTrace();
                }
            }
        }
    }

    public void stopRequester()
    {
        _blnSend = false;
    }

    public Connection getConnection()
    {
        return _connReqQEms;
    }

    public FileOutputStream getFileStream()
    {
        return _fioStream;
    }

}