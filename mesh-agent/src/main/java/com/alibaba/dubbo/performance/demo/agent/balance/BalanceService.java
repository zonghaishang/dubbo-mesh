package com.alibaba.dubbo.performance.demo.agent.balance;

import java.net.SocketAddress;

/**
 * @author 景竹 2018/6/8
 */
public interface BalanceService {
    /*public static int getRandom(int id) {
            return ports[id % ports.length];
        }*/
    int getRandom(int id);

    int getId();

    void releaseCount(SocketAddress port);

    void releaseCount(int port);
}
