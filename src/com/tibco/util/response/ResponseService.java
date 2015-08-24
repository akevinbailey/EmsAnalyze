/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.response;

import com.tibco.tibjms.TibjmsQueueConnectionFactory;
import com.tibco.util.strcuts.ConnectionParams;
import javax.jms.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Title:        ResponseService
 * Description:  This is the class creates a thread to respond to the EMS ping message from the EmsAnalyze app.
 * @author A. Kevin Bailey
 * @version 0.1
 */
@SuppressWarnings({"UnusedDeclaration", "CanBeFinal", "unused", "UnusedAssignment"})
public class ResponseService implements Runnable
{
    private ConnectionParams _connParams;
    private QueueConnection _connQEms;
    private String _strClientId;
    private boolean _blnListen;
    private SimpleDateFormat _formatDate;

    public ResponseService(ConnectionParams connParams)
    {
        _connParams = connParams;
        _blnListen = true;
        _strClientId = _connParams.clientID == null ? null : _connParams.clientID + "_responder";
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
            connQFactory = new TibjmsQueueConnectionFactory(_connParams.emsURL, _strClientId, _connParams.envSSL);
            if (_connParams.username == null || _connParams.username.equals(""))
                _connQEms = connQFactory.createQueueConnection();
            else
                _connQEms = connQFactory.createQueueConnection(_connParams.username, _connParams.password);

            _connQEms.start();
            sesQEms = _connQEms.createQueueSession(false, _connParams.ackMode);
            reqQueue = sesQEms.createQueue(_connParams.reqDest);
            qReceiver = sesQEms.createReceiver(reqQueue);

            while (_blnListen) {
                msg = (TextMessage)qReceiver.receive();

                if (_connParams.isEcho) {
                    String strOut = _formatDate.format(new Date()) + "\t" + msg.getJMSMessageID();
                    System.out.println(" " + strOut);
                }

                if (msg.getJMSReplyTo() != null) {
                    qSender = sesQEms.createSender((Queue)msg.getJMSReplyTo());
                    qSender.setDeliveryMode(_connParams.deliveryMode);
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

    public void stopListener()
    {
        _blnListen = false;
    }

    public Connection getConnection()
    {
        return _connQEms;
    }
}
