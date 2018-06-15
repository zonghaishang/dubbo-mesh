package com.alibaba.dubbo.performance.demo.agent.balance;

import java.net.SocketAddress;

/**
 * @author 景竹 2018/6/8
 */
public interface BalanceService {
    int getRandom(int id);

    int getRandom(int id, int batchSize);

    int getRandom();

    int getId();

    int currentId();

    void releaseCount(SocketAddress port);

    void releaseCount(int port);

    void addCount(int count,int port);

    int getInitNode();
}
