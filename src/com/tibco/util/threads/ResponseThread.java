/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.threads;

import com.tibco.tibjms.TibjmsQueueConnectionFactory;
import com.tibco.util.strcuts.ConnectionParams;
import javax.jms.*;
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
public class ResponseThread implements Runnable
{
    private ConnectionParams _connResParams;
    private QueueConnection _connResQEms;
    private String _strClientId;
    private boolean _blnListen;
    private SimpleDateFormat _formatDate;

    public ResponseThread(ConnectionParams connParams, int threadId)
    {
        _connResParams = connParams;
        _blnListen = true;
        _strClientId = _connResParams.clientID == null ? null : _connResParams.clientID + threadId + "_responder";
        _formatDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());

    }

    public void run()
    {
        TibjmsQueueConnectionFactory connQFactory;
        QueueSession sesQEms;
        QueueRequestor qRequester;
        QueueSender qSender;
        QueueReceiver qReceiver;
        Queue reqQueue;
        TextMessage msg;

        try {
            connQFactory = new TibjmsQueueConnectionFactory(_connResParams.emsURL, _strClientId, _connResParams.envSSL);
            if (_connResParams.username == null || _connResParams.username.equals(""))
                _connResQEms = connQFactory.createQueueConnection();
            else
                _connResQEms = connQFactory.createQueueConnection(_connResParams.username, _connResParams.password);

            _connResQEms.start();
            sesQEms = _connResQEms.createQueueSession(false, _connResParams.ackMode);
            reqQueue = sesQEms.createQueue(_connResParams.reqDest);
            qReceiver = sesQEms.createReceiver(reqQueue);

            while (_blnListen) {
                msg = (TextMessage)qReceiver.receive();

                if (_connResParams.isEcho) {
                    String strOut = String.format("%-25s %-38s", _formatDate.format(new Date()), msg.getJMSMessageID());
                    System.out.println(" " + strOut);
                }

                if (msg.getJMSReplyTo() != null) {
                    qSender = sesQEms.createSender((Queue)msg.getJMSReplyTo());
                    qSender.setDeliveryMode(_connResParams.deliveryMode);
                    if (!((Queue) msg.getJMSReplyTo()).getQueueName().startsWith("$TMP$.") && msg.getJMSCorrelationID() == null) {
                        msg.setJMSCorrelationID(msg.getJMSMessageID());
                    }

                    msg.setJMSDestination(null);
                    msg.setJMSReplyTo(null);
                    qSender.send(msg);
                }
            }
        }
        catch (JMSException jex){
            if (!jex.getMessage().equals("Thread has been interrupted"))
                jex.printStackTrace();
        }
    }

    public void stopResponder()
    {
        _blnListen = false;
    }

    public Connection getConnection()
    {
        return _connResQEms;
    }
}
