package com.alibaba.dubbo.performance.demo.agent.util;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 景竹 2018/5/12
 */
public class WeightUtil {
    //private static int[] ports = new int[]{30000,30001,30001,30001,30002,30002,30002,30002};
    private static int[] ports = new int[]{30000};
    private static Random random = new Random();
    private static AtomicInteger atomicInteger = new AtomicInteger(0);
    private static int i = 0;

    public static int getRandom(){
        return ports[random.nextInt(ports.length)];
    }

    public static int getId(){
        return atomicInteger.getAndIncrement();
    }
    /**
     * i++有线程不安全，然而并没有什么大影响
     */
    public static int getRandom2(){
        return ports[i++ % ports.length];
    }
    public static int getRandom(int id){
        return ports[id % ports.length];
    }
}
