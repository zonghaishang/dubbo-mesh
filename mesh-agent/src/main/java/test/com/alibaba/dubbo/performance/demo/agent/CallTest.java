package test.com.alibaba.dubbo.performance.demo.agent;

import org.apache.commons.lang3.RandomStringUtils;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.asynchttpclient.AsyncHttpClient;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author 景竹 2018/5/14
 */
public class CallTest {

    private static AsyncHttpClient asyncHttpClient = org.asynchttpclient.Dsl.asyncHttpClient();

    private static ResponseEntity ok = new ResponseEntity("OK", HttpStatus.OK);
    private static ResponseEntity error = new ResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    static Random r = new Random(1);
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        doCall();
    }

    public static void doCall() throws ExecutionException, InterruptedException {
        int max = 100;
        int THREAD_NUMBER = 100;
        int POOL_SIZE = 100;
        final AtomicLong count = new AtomicLong(0);
        FutureTask[] futures = new FutureTask[THREAD_NUMBER];
        ExecutorService executor = new ThreadPoolExecutor
                (POOL_SIZE,POOL_SIZE,10,TimeUnit.SECONDS,new LinkedBlockingQueue<>()
                );

        for ( Integer i = 0; i < futures.length; i++) {
            futures[i] = new FutureTask(() -> {
                for (int j = 0; j < max; j++) {
                    String str = RandomStringUtils.random(r.nextInt(1024), true, true);

                    String url = "http://127.0.0.1:20000";

                    DeferredResult<ResponseEntity> result = new DeferredResult<>();

                    org.asynchttpclient.Request request = org.asynchttpclient.Dsl.post(url)
                            .addFormParam("interface", "com.alibaba.dubbo.performance.demo.provider.IHelloService")
                            .addFormParam("method", "hash")
                            .addFormParam("parameterTypesString", "Ljava/lang/String;")
                            .addFormParam("parameter", str)
                            .build();
                    ListenableFuture<Response> responseFuture = asyncHttpClient.executeRequest(request);
                    /*Runnable callback = () -> {
                        try {
                            // 检查返回值是否正确,如果不正确返回500。有以下原因可能导致返回值不对:
                            // 1. agent解析dubbo返回数据不对
                            // 2. agent没有把request和dubbo的response对应起来
                            //String value = responseFuture.get().getResponseBody();
                            if (String.valueOf(str.hashCode()).equals(value)){
                                result.setResult(ok);
                            } else {
                                count.getAndIncrement();
                                result.setResult(error);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    };
                    responseFuture.addListener(callback, null);*/

                    return result;
                }
                return null;
            });

        }
        for (FutureTask future : futures) {
            executor.submit(future);
        }

        long start = System.currentTimeMillis();
        System.out.println("start:..............");
        for (Future future : futures) {
            future.get();
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("错误数："+count);
        System.out.println("总共耗时：" + (cost));
        System.out.println("总共并发数：" + THREAD_NUMBER);
        System.out.println("总共调用次数：" + max * THREAD_NUMBER);
    }
}
