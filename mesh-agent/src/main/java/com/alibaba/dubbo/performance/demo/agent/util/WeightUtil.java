package com.alibaba.dubbo.performance.demo.agent.util;

import java.util.Random;

/**
 * @author 景竹 2018/5/12
 */
public class WeightUtil {
    private static int[] ports = new int[]{8871,8872,8872,8873,8873,8873};
    //private static int[] ports = new int[]{8871};
    private static Random random = new Random();

    public static int getRandom(){
        return ports[random.nextInt(ports.length)];
    }
}
