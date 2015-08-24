/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.helpers;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Title:        EmsAnalyzeClose
 * Description:  This is class hooks into the JVM and is run before the JVM exits.
 * @author A. Kevin Bailey
 * @version 0.3
 */
@SuppressWarnings({"UnusedDeclaration", "unused"})
public class EmsAnalyzeClose extends Thread
{
    private ArrayList<Connection> _alJmsConnections;
    private ArrayList<FileOutputStream> _alFileOutputs;

    public EmsAnalyzeClose() 
    {
        _alJmsConnections = new ArrayList<Connection>(5);
        _alFileOutputs = new ArrayList<FileOutputStream>(5);
    }

    public EmsAnalyzeClose(ArrayList<Connection> alJmsConnections, ArrayList<FileOutputStream> alFileOutputs)
    {
        _alJmsConnections = alJmsConnections;
        _alFileOutputs = alFileOutputs;
    }

    public void addJmsConnection(Connection jmsConnections)
    {
        _alJmsConnections.add(jmsConnections);
    }

    public void addFileStream(FileOutputStream fileOutputs)
    {
        _alFileOutputs.add(fileOutputs);
    }

    public void setJmsConnections(ArrayList<Connection> alJmsConnections)
    {
        _alJmsConnections = alJmsConnections;
    }

    public void setFileOutputs(ArrayList<FileOutputStream> alFileOutputs)
    {
        _alFileOutputs = alFileOutputs;
    }

    public void clear() {
        _alJmsConnections.clear();
        _alFileOutputs.clear();
    }

    public void run()
    {
        if (_alJmsConnections != null && !_alJmsConnections.isEmpty())
        {
            for (Connection c: _alJmsConnections) {
                try {
                    if (c != null) c.close();
                }
                catch (JMSException jex) {
                    jex.printStackTrace();
                }
            }
        }

        if (_alFileOutputs != null && !_alFileOutputs.isEmpty())
        {
            for (FileOutputStream f: _alFileOutputs) {
                try {
                    if (f != null) f.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        System.out.println(" Test complete.");
    }
}
