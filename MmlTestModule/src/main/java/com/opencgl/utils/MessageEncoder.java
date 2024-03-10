package com.opencgl.utils;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * @author Chance.W
 * @date 2020/2/7-10:12
 */
public interface MessageEncoder<T> {
    void encode(IoSession var1, T var2, ProtocolEncoderOutput var3) throws Exception;
}
