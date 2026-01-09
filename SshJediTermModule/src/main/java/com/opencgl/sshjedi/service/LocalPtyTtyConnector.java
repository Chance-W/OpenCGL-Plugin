package com.opencgl.sshjedi.service;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.techsenger.jeditermfx.core.Questioner;
import com.techsenger.jeditermfx.core.TtyConnector;


import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalPtyTtyConnector implements TtyConnector {

    private static final Logger logger = LoggerFactory.getLogger(LocalPtyTtyConnector.class);

    private final PtyProcess ptyProcess;
    private final InputStreamReader reader;
    private final AtomicBoolean closed = new AtomicBoolean();

    public LocalPtyTtyConnector(PtyProcess ptyProcess) {
        this.ptyProcess = ptyProcess;
        this.reader = new InputStreamReader(ptyProcess.getInputStream(), StandardCharsets.UTF_8);
    }

    @Override
    public int read(char[] chars, int offset, int length) throws IOException {
        return reader.read(chars, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        if (ptyProcess.isAlive()) {
            ptyProcess.getOutputStream().write(bytes);
            ptyProcess.getOutputStream().flush();
        }
    }

    @Override
    public void write(String s) throws IOException {
        write(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return ptyProcess != null && ptyProcess.isAlive();
    }



    @Override
    public int waitFor() throws InterruptedException {
        return ptyProcess.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "Local Terminal";
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            reader.close();
        } catch (Exception error) {
            logger.warn("Error closing local terminal reader", error);
        }
        try {
            if (ptyProcess != null) {
                ptyProcess.destroyForcibly();
            }
        } catch (RuntimeException error) {
            logger.warn("Error closing local terminal process", error);
        }
    }

    @Override
    public void resize(Dimension termSize) {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            ptyProcess.setWinSize(new WinSize(termSize.width, termSize.height));
        }
    }

    @Override
    public void resize(Dimension termSize, Dimension pixelSize) {
        resize(termSize);
    }

    @Override
    public boolean init(Questioner questioner) {
        return true;
    }
}
