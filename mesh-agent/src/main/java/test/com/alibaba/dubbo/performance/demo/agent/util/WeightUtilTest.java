package test.com.alibaba.dubbo.performance.demo.agent.util;

import com.alibaba.dubbo.performance.demo.agent.util.WeightUtil;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

/**
 * WeightUtil Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>五月 14, 2018</pre>
 */
public class WeightUtilTest {

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
    }

    /**
     * Method: getRandom()
     */
    @Test
    public void testGetRandom() throws Exception {
        for (int i = 0; i < 100; i++) {
            System.out.println(WeightUtil.getRandom());
        }
    }


} 
