package com.alibaba.dubbo.performance.demo.agent.util;

import java.util.Random;

/**
 * @author 景竹 2018/5/12
 */
public class WeightUtil {
    private static int[] ports = new int[]{30000,30001,30002,30001,30002,30002};
    //private static int[] ports = new int[]{30000};
    private static Random random = new Random();

    public static int getRandom(){
        return ports[random.nextInt(ports.length)];
    }
    public static int getRandom(int id){
        return ports[id % ports.length];
    }
}
