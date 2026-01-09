package com.opencgl.sshjedi.service;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.techsenger.jeditermfx.core.Questioner;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;


import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JschTtyConnector implements TtyConnector {

    private static final Logger logger = LoggerFactory.getLogger(JschTtyConnector.class);

    private final Session session;
    private final ChannelShell channel;
    private final InputStreamReader reader;
    private final OutputStream outputStream;
    private final AtomicBoolean closed = new AtomicBoolean();

    public JschTtyConnector(Session session, ChannelShell channel, InputStream inputStream, OutputStream outputStream) {
        this.session = session;
        this.channel = channel;
        this.reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        this.outputStream = outputStream;
    }

    @Override
    public int read(char[] chars, int offset, int length) throws IOException {
        return reader.read(chars, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        if (channel != null && channel.isConnected()) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    @Override
    public void write(String s) throws IOException {
        write(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }


    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected()) {
            Thread.sleep(100);
        }
        return channel.getExitStatus();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "SSH: " + session.getUserName() + "@" + session.getHost();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            reader.close();
        } catch (Exception error) {
            logger.warn("Error closing SSH connector reader", error);
        }
        try {
            outputStream.close();
        } catch (Exception error) {
            logger.warn("Error closing SSH connector output stream", error);
        }
        try {
            if (channel != null) {
                channel.disconnect();
            }
        } catch (RuntimeException error) {
            logger.warn("Error closing SSH connector channel", error);
        }
        try {
            if (session != null) {
                session.disconnect();
            }
        } catch (RuntimeException error) {
            logger.warn("Error closing SSH connector session", error);
        }
    }

    @Override
    public void resize(Dimension termSize) {
        if (channel != null && channel.isConnected()) {
            channel.setPtySize(termSize.width, termSize.height, termSize.width * 8, termSize.height * 16);
        }
    }

    @Override
    public void resize(Dimension termSize, Dimension pixelSize) {
        if (channel != null && channel.isConnected()) {
            channel.setPtySize(termSize.width, termSize.height, pixelSize.width, pixelSize.height);
        }
    }

    @Override
    public boolean init(Questioner questioner) {
        return true;
    }
}
