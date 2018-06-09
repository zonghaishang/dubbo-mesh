package com.alibaba.dubbo.performance.demo.agent.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * @author 景竹 2018/6/8
 */
public class BalanceServiceImpl implements BalanceService {
    //Logger logger = LoggerFactory.getLogger(BalanceServiceImpl.class);
    private static AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final int MASK = 3;
    private static final int MAX_NUM = 200;

    public static AtomicIntegerArray counter = new AtomicIntegerArray(3);

    private static int[] ports = new int[]{
            30000, 30000,
            30001, 30001, 30001,
            30002, 30002, 30002,
    };

    //private static int[] ports = new int[]{30000,30001,30001};

    @Override
    public int getRandom(int id) {
        int port = ports[id % ports.length];

        //port计数在数组中的位置，取最后两byte的值，得到0、1、2
        int index = port & MASK;
        //超过阀值会线程溢出，则再选一次
        if(counter.get(index) > MAX_NUM){
            //遍历找到没满的port,优先large
            for (int i = 2; i >= 0; i--) {
                if (counter.get(i) <= MAX_NUM) {
                    counter.incrementAndGet(i);
                    return 30000 + i;
                }
            }
            //全满，直接返回
            return 0;
        }
        counter.incrementAndGet(index);
        return port;
    }

    @Override
    public int getId() {
        return atomicInteger.getAndIncrement();
    }

    @Override
    public void releaseCount(SocketAddress port){
        int remotePort = ((InetSocketAddress)port).getPort();
        counter.decrementAndGet(remotePort & MASK);
    }

    @Override
    public void releaseCount(int port){
        counter.decrementAndGet(port & MASK);
    }

}
