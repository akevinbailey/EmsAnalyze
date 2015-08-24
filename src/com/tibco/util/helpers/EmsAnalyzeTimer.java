/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.helpers;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimerTask;

/**
 * Title:        <p>
 * Description:  <p>
 * @author A. Kevin Bailey
 * @version 0.1
 */
@SuppressWarnings({"UnusedAssignment", "WeakerAccess", "unused"})
public class EmsAnalyzeTimer extends TimerTask
{

    public void run()
    {
        SimpleDateFormat formatter;
        formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());

    }
}
