/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.cifs;

import org.mule.DefaultMuleMessage;
import org.mule.transport.AbstractMuleMessageFactory;
import org.mule.transport.file.FileConnector;
import org.mule.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import jcifs.smb.SmbFile;

public class SmbMuleMessageFactory extends AbstractMuleMessageFactory
{
	private static final Logger log = LoggerFactory.getLogger(SmbMessageRequesterFactory.class);

    @Override
    protected Class<?>[] getSupportedTransportMessageTypes()
    {
        return new Class[] { SmbFile.class };
    }

    @Override
    protected Object extractPayload(Object transportMessage, String encoding) throws Exception
    {
        SmbFile file = (SmbFile) transportMessage;

        InputStream stream = file.getInputStream();
        byte[] data = IOUtils.toByteArray(stream);
        stream.close();

        return data;
    }

    @Override
    protected void addProperties(DefaultMuleMessage muleMessage, Object transportMessage) throws Exception
    {
        SmbFile file = (SmbFile) transportMessage;

        muleMessage.setInboundProperty(FileConnector.PROPERTY_ORIGINAL_FILENAME, file.getName());
        muleMessage.setInboundProperty(FileConnector.PROPERTY_FILE_SIZE, file.length());
        
        muleMessage.setOutboundProperty(FileConnector.PROPERTY_FILENAME, file.getName());
        
        log.debug("SMB MuleMessage: {}", muleMessage);
    }
}
