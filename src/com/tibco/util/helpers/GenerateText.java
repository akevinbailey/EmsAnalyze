/*
 * Copyright 2012.  TIBCO Software Inc.  ALL RIGHTS RESERVED.
 */
package com.tibco.util.helpers;

import java.util.Random;

/**
 * Title:        GenerateText
 * Description:  This class generate test of a certain length.
 * @author A. Kevin Bailey
 * @version 0.5
 */

@SuppressWarnings({"UnusedDeclaration", "unused"})
public final class GenerateText
{
    public static String genChars(int sizeOfMsg)
    {
        String strGen = "";

        if (sizeOfMsg > 0) {
            Random rand = new Random();
            byte bytGen[] = new byte[sizeOfMsg];
            rand.nextBytes(bytGen);
            strGen = new String(bytGen);
        }

        return strGen;
    }

    public static String genChars(char ch, int sizeOfMsg)
    {
        String strGen = "";

        if (sizeOfMsg > 0) {
            char[] chGen = new char[sizeOfMsg];
            for (int i=0; i < sizeOfMsg; i++) chGen[i] = ch;
            strGen = new String(chGen);
        }

        return strGen;
    }
}
