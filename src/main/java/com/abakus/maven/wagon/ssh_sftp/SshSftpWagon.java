package com.abakus.maven.wagon.ssh_sftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.resource.Resource;
import org.codehaus.plexus.util.StringUtils;

public class SshSftpWagon extends AbstractWagon {
    private SftpUtility _sftp = null;

    private SftpUtility sftp() {
        if (this._sftp == null) {
            throw new RuntimeException("openConnectionInternal() was not called before get/put");
        } else {
            return this._sftp;
        }
    }

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
        this._sftp = new SftpUtility(this.repository.getHost(), this.authenticationInfo);
    }

    @Override
    protected void closeConnection() throws ConnectionException {
        this._sftp = null;
    }

    @Override
    public void get(String resourceName, File destination) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        final Resource resource = new Resource( StringUtils.replace( resourceName, "\\", "/" ) );
        this.fireGetInitiated( resource, destination );

        final Path localFolder = destination.toPath().getParent();
        try {
            Files.createDirectories(localFolder);
        } catch (Exception e) {
            throw new TransferFailedException("Unable to create directories for [" + localFolder.toString() + "]");
        }

        this.fireGetStarted( resource, destination);
        this.sftp().lsTime(resourceName);
        this.sftp().get(resourceName, destination);

        this.postProcessListeners(resource, destination, TransferEvent.REQUEST_GET);

        this.fireGetCompleted(resource, destination);
    }

    @Override
    public boolean getIfNewer(String resourceName, File destination, long timestamp) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        final long remoteTS = this.sftp().lsTime(resourceName);
        if (timestamp > remoteTS)
            return false;

        this.sftp().get(resourceName, destination);
        return true;
    }

    @Override
    public void put(File source, String destination) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        final Resource resource = new Resource(destination);
        this.firePutInitiated(resource, source);

        if (!source.exists())
            throw new ResourceDoesNotExistException("Local file [" + source + "] does not exists.");

        resource.setContentLength(source.length());
        resource.setLastModified(source.lastModified());
        this.firePutStarted(resource, source);
        this.sftp().put(source, destination);

        this.postProcessListeners(resource, source, TransferEvent.REQUEST_PUT);
        this.firePutCompleted(resource, source);
    }
}
