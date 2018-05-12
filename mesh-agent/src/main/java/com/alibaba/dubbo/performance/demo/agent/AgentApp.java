package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.consumer.ConsumerServer;
import com.alibaba.dubbo.performance.demo.agent.provider.ProviderService;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

@SpringBootApplication
public class AgentApp {
    // agent会作为sidecar，部署在每一个Provider和Consumer机器上
    // 在Provider端启动agent时，添加JVM参数-Dtype=provider -Dserver.port=30000 -Ddubbo.protocol.port=20889
    // 在Consumer端启动agent时，添加JVM参数-Dtype=consumer -Dserver.port=20000
    // 添加日志保存目录: -Dlogs.dir=/path/to/your/logs/dir。请安装自己的环境来设置日志目录。

    public static void main(String[] args) throws Exception {
        System.setProperty("server.port","20000");
        System.setProperty("type",Constants.CONSUMER);
        System.setProperty("etcd.url","http://127.0.0.1:2379");

        String type = System.getProperty(Constants.TYPE);
        if(!StringUtils.isEmpty(type) && type.equalsIgnoreCase(Constants.CONSUMER)){
            ConsumerServer.initConsumerAgent();
        }else {
            ProviderService.initProviderAgent();
        }
        //SpringApplication.run(AgentApp.class,args);
    }
}
