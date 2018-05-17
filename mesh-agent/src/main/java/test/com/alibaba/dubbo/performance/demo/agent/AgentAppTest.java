package test.com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.provider.ProviderService;
import com.alibaba.dubbo.performance.demo.agent.util.Constants;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

/**
 * AgentApp Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>五月 12, 2018</pre>
 */
public class AgentAppTest {

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
    }

    /**
     * Method: main(String[] args)
     */
    @Test
    public void testMain() throws Exception {
        System.setProperty("etcd.url","http://127.0.0.1:2379");
        System.setProperty(Constants.SERVER_PORT,"30000");
        System.setProperty(Constants.DUBBO_PROTOCOL_PORT,"20880");
        ProviderService.initProviderAgent();
    }


} 
