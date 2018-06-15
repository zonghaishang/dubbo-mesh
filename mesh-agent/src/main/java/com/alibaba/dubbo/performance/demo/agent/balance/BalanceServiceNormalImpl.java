package com.alibaba.dubbo.performance.demo.agent.balance;

import java.net.SocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * @author 景竹 2018/6/9
 */
public class BalanceServiceNormalImpl implements BalanceService{
    private static AtomicInteger atomicInteger = new AtomicInteger(0);
    private Random random = new Random();

    private static int[] ports = new int[]{
            30000, 30000,
            30001, 30001, 30001,
            30002, 30002, 30002,
    };
    //private static int[] ports = new int[]{30000};

    @Override
    public int getRandom(int id) {
        return ports[id % ports.length];
    }

    @Override
    public int getRandom(int id, int batchSize) {
        return 0;
    }

    @Override
    public int getRandom() {
        return ports[random.nextInt(ports.length)];
    }

    @Override
    public int getId() {
        return atomicInteger.incrementAndGet();
    }

    @Override
    public int currentId() {
        return atomicInteger.get();
    }

    @Override
    public void releaseCount(SocketAddress port) {

    }
    @Override
    public void releaseCount(int port){
    }

    @Override
    public void addCount(int count,int port) {

    }

    @Override
    public int getInitNode() {
        return 0;
    }
}
