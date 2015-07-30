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

import org.mule.api.MuleMessage;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.transport.AbstractMessageRequester;
import org.mule.util.StringUtils;

import java.io.FilenameFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.SmbFile;

public class SmbMessageRequester extends AbstractMessageRequester
{
    protected final SmbConnector smbConnector;

    public SmbMessageRequester(InboundEndpoint endpoint)
    {
        super(endpoint);
        this.smbConnector = (SmbConnector)endpoint.getConnector();
    }

    /**
     * Make a specific request to the underlying transport
     *
     * @param timeout the maximum time the operation should block before returning.
     *            The call should return immediately if there is data available. If
     *            no data becomes available before the timeout elapses, null will be
     *            returned
     * @return the result of the request wrapped in a MuleMessage object. Null will
     *         be returned if no data was avaialable
     * @throws Exception if the call to the underlying protocal cuases an exception
     */
    @Override
    protected MuleMessage doRequest(long timeout) throws Exception
    {
        FilenameFilter filenameFilter = null;
        if (endpoint.getFilter() instanceof FilenameFilter)
        {
            filenameFilter = (FilenameFilter)endpoint.getFilter();
        }

        EndpointURI uri = endpoint.getEndpointURI();
        String smbPath = null;
        if (SmbConnector.checkNullOrBlank(uri.getUser()) || SmbConnector.checkNullOrBlank(uri.getPassword()))
        {
            logger.warn("No user or password supplied. Attempting to connect with just smb://<host>/<path>");
            logger.info("smb://" + uri.getHost() + uri.getPath());
            smbPath = "smb://" + uri.getHost() + uri.getPath();
        }
        else
        {
            logger.info("smb://" + uri.getUser() + ":<password hidden>@" + uri.getHost() + uri.getPath());
            smbPath = "smb://" + uri.getUser() + ":" + uri.getPassword() + "@" + uri.getHost() + uri.getPath();
        }

        SmbFile[] files = new SmbFile(smbPath).listFiles();
        if (files == null || files.length == 0)
        {
            return null;
        }

        List<SmbFile> fileList = new ArrayList<SmbFile>();
        SmbFile file = null;
        for (int i = 0; i < files.length; i++)
        {
            file = files[i];
            if (file.isFile())
            {
                if (filenameFilter == null || filenameFilter.accept(null, file.getName()))
                {
                    long fileAge = smbConnector.getFileAge();
                    if (smbConnector.checkFileAge(file, fileAge))
                    {
                        fileList.add(file);
                        // only read the first one
                        break;
                    }
                }
            }
        }
        if (fileList.size() == 0)
        {
            return null;
        }

        // TODO why not use the fileList above?
        MuleMessage msg = createMuleMessage(file);
        postProcess(file, msg);
        return msg;
    }
    
    protected void postProcess(SmbFile file, MuleMessage message) throws Exception
    {
    	String moveToDir = smbConnector.getMoveToDirectory();
    	String moveToPattern = smbConnector.getMoveToPattern();
        if (!StringUtils.isEmpty(moveToDir))
        {
            String destinationFileName = file.getName();

            if (!StringUtils.isEmpty(moveToPattern))
            {
                destinationFileName = (smbConnector).getFilenameParser().getFilename(message, moveToPattern);
            }

            SmbFile dest;
            EndpointURI uri = endpoint.getEndpointURI();

            if (SmbConnector.checkNullOrBlank(uri.getUser()) || SmbConnector.checkNullOrBlank(uri.getPassword()))
            {
                dest = new SmbFile("smb://" + uri.getHost() + moveToDir + destinationFileName);
            }
            else
            {
            	String url = "smb://" + uri.getUser() + ":" + uri.getPassword() + "@" + uri.getHost() + moveToDir + destinationFileName;
                dest = new SmbFile(url);
            }

            logger.debug("dest: " + dest);

            try
            {
                file.renameTo(dest);
            }
            catch (Exception e)
            {
                throw new IOException(MessageFormat.format(
                    "Failed to rename file " + file.getName() + " to " + dest.getName() + ". Smb error! "
                                    + e.getMessage(), new Object[]{ file.getName(),
                        moveToDir + destinationFileName, e }));
            }

            logger.debug("Renamed processed file " + file.getName() + " to " + moveToDir
                         + destinationFileName);
        }
        else
        {
            try
            {
                file.delete();
            }
            catch (Exception e)
            {
                throw new IOException(MessageFormat.format("Failed to delete file " + file.getName()
                                                           + ". Smb error: " + e.getMessage(),
                    file.getName(), null));
            }

            if (logger.isDebugEnabled())
            {
                logger.debug("Deleted processed file " + file.getName());
            }
        }
    }
}
