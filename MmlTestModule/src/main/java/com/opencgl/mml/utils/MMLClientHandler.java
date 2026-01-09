package com.opencgl.mml.utils;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

import java.util.concurrent.BlockingQueue;

/**
 * @author Chance.W
 * @date 2020/2/7-9:56
 */
public class MMLClientHandler extends IoHandlerAdapter {
    private ClientInfo clientInfo;
    private BlockingQueue<Msg> queue;

    public MMLClientHandler(ClientInfo clientInfo, BlockingQueue<Msg> queue) {
        this.clientInfo = clientInfo;
        this.queue = queue;
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        session.write(HeartMsg.INST);
    }

    @Override
    public void sessionCreated(IoSession session) throws Exception {
        session.setAttribute("CLIENT_INFO_KEY", this.clientInfo);
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        ClientInfo info = (ClientInfo)session.getAttribute("CLIENT_INFO_KEY");
        Msg msg = new Msg();
      //  LoginCmd loginCmd = new LoginCmd(info.username, info.password);
        msg.cmd = new LoginCmd(info.username, info.password);
        session.write(msg);
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        Msg msg = (Msg)message;
        ClientInfo clientInfo = (ClientInfo)session.getAttribute("CLIENT_INFO_KEY");
        clientInfo.sessionId = msg.msgSessionHead.id;
        String sessionHeadWord = msg.msgSessionHead.word;
        if (sessionHeadWord.equalsIgnoreCase("DlgCon") || sessionHeadWord.equalsIgnoreCase("DlgLgn") || sessionHeadWord.equalsIgnoreCase("DlgEnd")) {
            this.queue.put(msg);
        }

    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        System.out.println(cause);
    }
}