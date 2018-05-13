package com.alibaba.dubbo.performance.demo.agent.registry;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpHelper {

    public static String getHostIp() {

        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return ip;
    }
}
