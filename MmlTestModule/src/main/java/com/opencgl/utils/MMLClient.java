package com.opencgl.utils;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Chance.W
 * @Description TODO
 * @date 2020/2/7-9:48
 */

public class MMLClient implements IMMLClient, ManagedClient, Testable {
    private IoConnector connector = null;
    private String hostname;
    private int port;
    public IoSession session;
    private BlockingQueue<Msg> queue;
    private int timeOut = 1000;
    private int idleTime;
    private String username;
    private Properties param;

    public MMLClient() {
    }

    @Override
    public void init(Properties param) throws Exception {
        this.param = param;
        this.connector = new NioSocketConnector();
        this.connector.getFilterChain().addFirst(MMLProtocolCodecFactory.class.getName(), new ProtocolCodecFilter(new MMLProtocolCodecFactory()));
        this.connector.getFilterChain().addAfter(MMLProtocolCodecFactory.class.getName(), HeartMsgFilter.class.getName(), new HeartMsgFilter());
        this.connector.setConnectTimeoutMillis(10000L);
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.username = param.getProperty("username");
        clientInfo.password = param.getProperty("password");
        this.username = param.getProperty("username");
        this.idleTime = Integer.valueOf(param.getProperty("idleTime", "120"));
        this.queue = new LinkedBlockingQueue();
        this.connector.getSessionConfig().setBothIdleTime(this.idleTime);
        this.connector.setHandler(new MMLClientHandler(clientInfo, this.queue));
        this.hostname = param.getProperty("hostname");
        this.port = Integer.valueOf(param.getProperty("port"));
        this.timeOut = Integer.valueOf(param.getProperty("timeOut", "1000"));
        byte[] b_servName = param.getProperty("servicename").getBytes();
        if (b_servName.length <= 8 && b_servName.length >= 1) {
            for (int i = 0; i < 8; ++i) {
                if (b_servName.length < 8 && i > b_servName.length - 1) {
                    MsgHead.MSG_HEAD_SERVICE_NAME[i] = 32;
                } else {
                    MsgHead.MSG_HEAD_SERVICE_NAME[i] = b_servName[i];
                }
            }

        } else {
            // throw new MMLException("", "MML0001", "Please check serviceName,it does not make right configuration");
        }
    }
    private ConnectFuture future;
    @Override
    public void start() throws Exception {
        if (this.connector.isDisposed()) {
            this.init(this.param);
        }

     //   ConnectFuture future = this.connector.connect(new InetSocketAddress(this.hostname, this.port));
        future = this.connector.connect(new InetSocketAddress(this.hostname, this.port));

        try {
            future.awaitUninterruptibly();
            this.session = future.getSession();
        } catch (Exception var3) {
            //  throw new Exception("MML0006", "Can't connect to MML Server!");
        }

        try {
            Thread.sleep(5000L);
        } catch (Exception var2) {
        }

    }

    private Msg sendCmd0(String message) throws Exception {
        Msg msg = new Msg();
        msg.cmd = new MsgCmd(message);
        this.queue.clear();
        this.session.write(msg);
        return this.syncRead();
    }

    private final Msg syncRead() throws Exception {
        try {
            return (Msg) this.queue.poll((long) this.timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException var2) {
            throw new Exception("MML0002", var2);
        }
    }

    @Override
    public MMLACK sendCmd(String message) throws Exception {
        Msg rtnMsg = null;
        try {
            rtnMsg = this.sendCmd0(message);
        } catch (Exception var4) {
            var4.printStackTrace();
            /*if (rtnMsg != null) {
                timeOut
              //  throw new Exception("MML0003", var4.getMessage() + ";Return Msg: " + rtnMsg.cmd.getCmd());
            } else {
              //  throw new Exception("MML0004", var4.getMessage() + ";Can not get return msg");
            }*/
        }
        return MMLAssert.parseReturnMessage(rtnMsg.cmd.getCmd());
    }

    private void sendLogoutCmd() throws Exception {
        Msg msg = new Msg();
        msg.cmd = new LogoutCmd(this.username);
        this.queue.clear();
        this.session.write(msg);
    }

    @Override
    public void stop() throws Exception {
        try {
            this.sendLogoutCmd();
        } catch (Exception var5) {
            throw var5;
        } finally {
            this.session.close(true).awaitUninterruptibly();
        }

    }
    public Boolean checkState(){
        return  future.getSession().isConnected();
    }
    @Override
    public void destory() throws Exception {
        this.connector.dispose();
    }

    @Override
    public String getType() {
        return "MMLClient";
    }

    @Override
    public String test() throws Exception {
        this.init(this.param);
        this.start();
        this.stop();
        return "Connect MML Successfully!";
    }
}
