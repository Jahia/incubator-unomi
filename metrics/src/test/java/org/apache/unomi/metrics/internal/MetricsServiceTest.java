/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.metrics.internal;

import org.apache.unomi.metrics.MetricsService;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MetricsServiceTest {

    class Worker implements Callable<BigInteger> {

        MetricsService metricsService;
        String workerName;
        int nbLoops = 1000;

        public Worker(MetricsService metricsService, String workerName, int nbLoops) {
            this.metricsService = metricsService;
            this.workerName = workerName;
            this.nbLoops = nbLoops;
        }

        @Override
        public BigInteger call() {
            long startTime = System.currentTimeMillis();
            BigInteger n1=BigInteger.ZERO,n2=BigInteger.ONE,n3=BigInteger.ZERO;
            for (int i = 0; i < nbLoops; i++) {
                n3 = n1.add(n2);
                n1 = n2;
                n2 = n3;
            }
            if (metricsService != null) {
                metricsService.updateTimer(workerName, startTime);
            }
            return n3;
        }
    }

    @Test
    public void testMetricsImpact() throws InterruptedException {
        int workerCount = 100000;
        System.out.println("Free memory=" + humanReadableByteCount(Runtime.getRuntime().freeMemory(), false));
        System.out.println("Testing with metrics deactivated...");
        ExecutorService executorService = Executors.newFixedThreadPool(1000);
        List<Callable<BigInteger>> todo = new ArrayList<Callable<BigInteger>>(workerCount);
        MetricsServiceImpl metricsService = new MetricsServiceImpl();
        metricsService.setActivated(false);
        Random random = new Random();
        long withoutMetricsStartTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new Worker(null, "worker-" + i, 1000+ random.nextInt(1000)));
        }
        List<Future<BigInteger>> answers = executorService.invokeAll(todo);
        long withoutMetricsTotalTime = System.currentTimeMillis() - withoutMetricsStartTime;
        System.out.println("Total time without metrics=" + withoutMetricsTotalTime + "ms");
        assertEquals("Metrics should be empty", 0, metricsService.getMetrics().size());
        System.out.println("Free memory=" + humanReadableByteCount(Runtime.getRuntime().freeMemory(), false));

        System.out.println("Testing with metrics activated (but no callees)...");
        todo.clear();
        metricsService.setActivated(true);
        assertEquals("Callees should be completely empty", metricsService.getCalleesStatus().size(), 0);
        long withMetricsStartTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new Worker(metricsService, "worker-" + i, 1000+ random.nextInt(1000)));
        }
        answers = executorService.invokeAll(todo);
        long withMetricsTotalTime = System.currentTimeMillis() - withMetricsStartTime;
        System.out.println("Total time with metrics (no callees) =" + withMetricsTotalTime + "ms");
        assertEquals("Metrics count is not correct", workerCount, metricsService.getMetrics().size());
        System.out.println("Free memory=" + humanReadableByteCount(Runtime.getRuntime().freeMemory(), false));

        System.out.println("Testing with metrics activated (all callees activated)...");
        todo.clear();
        metricsService.setActivated(true);
        metricsService.setCalleeActivated("*", true);
        assertNotEquals("Callees should not be completely empty", metricsService.getCalleesStatus().size(), 0);
        long withMetricsAndCalleesStartTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new Worker(metricsService, "worker-" + i, 1000+ random.nextInt(1000)));
        }
        answers = executorService.invokeAll(todo);
        long withMetricsAndCalleesTotalTime = System.currentTimeMillis() - withMetricsAndCalleesStartTime;
        System.out.println("Total time with metrics (with callees)=" + withMetricsAndCalleesTotalTime + "ms");
        assertEquals("Metrics count is not correct", workerCount, metricsService.getMetrics().size());
        System.out.println("Free memory=" + humanReadableByteCount(Runtime.getRuntime().freeMemory(), false));
    }

    @Test
    public void testStackTraceGenerationSpeed() {
        long startWithException = System.currentTimeMillis();
        for (long i = 0; i < 100000; i++) {
            String stackTrace = Arrays.toString(new Exception().getStackTrace());
            int stackTraceHash = stackTrace.hashCode();
        }
        System.out.println("Total time using exception and getStackTrace = " + (System.currentTimeMillis()
                - startWithException) + "ms.");

        long startWithThrowable = System.currentTimeMillis();
        for (long i = 0; i < 100000; i++) {
            String stackTrace = Arrays.toString(new Throwable().getStackTrace());
            int stackTraceHash = stackTrace.hashCode();
        }
        System.out.println("Total time using throwable and getStackTrace = " + (System.currentTimeMillis()
                - startWithThrowable) + "ms.");

        long startWithThread = System.currentTimeMillis();
        for (long i = 0; i < 100000; i++) {
            String stackTtrace = Arrays.toString(Thread.currentThread().getStackTrace());
            int stackTraceHash = stackTtrace.hashCode();
        }
        System.out.println("Total time using current thread and getStackTrace = " + (System.currentTimeMillis()
                - startWithThread) + "ms.");

        System.out.println("Free memory=" + humanReadableByteCount(Runtime.getRuntime().freeMemory(), false));

    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}