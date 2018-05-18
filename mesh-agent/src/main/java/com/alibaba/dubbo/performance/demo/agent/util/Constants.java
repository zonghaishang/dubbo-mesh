package com.alibaba.dubbo.performance.demo.agent.util;

/**
 * @author 景竹 2018/5/12
 */
public class Constants {
    public static final String SERVER_PORT = "server.port";
    public static final String NETTY_PORT = "netty.port";
    public static final String CONSUMER = "consumer";
    public static final String TYPE = "type";
    public static final String SERVER_NAME = "com.alibaba.dubbo.performance.demo.provider.IHelloService";
    public static final String ETCE = "etcd.url";
    public static final String DUBBO_PROTOCOL_PORT = "dubbo.protocol.port";


    public static final int RECEIVE_BUFFER_SIZE = 15* 1024;
    public static final int SEND_BUFFER_SIZE = 15 * 1024;
    public static final int CONNECT_TIME_OUT = 200;

    public static final int EVENT_LOOP_NUM = 8;

}
