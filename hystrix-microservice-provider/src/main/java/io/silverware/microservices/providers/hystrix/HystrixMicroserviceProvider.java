/*
 * -----------------------------------------------------------------------\
 * SilverWare
 *  
 * Copyright (C) 2016 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package io.silverware.microservices.providers.hystrix;

import io.silverware.microservices.Context;
import io.silverware.microservices.providers.MicroserviceProvider;
import io.silverware.microservices.silver.HttpServerSilverService;
import io.silverware.microservices.silver.HystrixSilverService;
import io.silverware.microservices.silver.http.ServletDescriptor;
import io.silverware.microservices.util.Utils;

import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Properties;

/**
 * Publishes Hystrix metrics stream generated by executed Hystrix commands.
 */
public class HystrixMicroserviceProvider implements MicroserviceProvider, HystrixSilverService {

   private static final Logger log = LogManager.getLogger(HystrixMicroserviceProvider.class);

   private static final String SERVLET_NAME = "HystrixMetricsStreamServlet";

   private Context context;

   @Override
   public void initialize(final Context context) {
      this.context = context;

      context.getProperties().putIfAbsent(HYSTRIX_METRICS_ENABLED, "false");
      context.getProperties().putIfAbsent(HYSTRIX_METRICS_PATH, "hystrix.stream");
   }

   @Override
   public Context getContext() {
      return context;
   }

   @Override
   public void run() {
      log.info("Hello from Hystrix microservice provider!");

      if (!isMetricsEnabled()) {
         if (log.isDebugEnabled()) {
            log.debug("Hystrix metrics stream disabled.");
         }
         return;
      }

      try {
         if (log.isDebugEnabled()) {
            log.debug("Waiting for the HTTP server microservice provider.");
         }

         HttpServerSilverService http = null;
         while (!Thread.currentThread().isInterrupted()) {
            if (http == null) {
               http = (HttpServerSilverService) context.getProvider(HttpServerSilverService.class);

               if (http != null) {
                  if (log.isDebugEnabled()) {
                     log.debug("Discovered HTTP Silverservice: " + http.getClass().getName());
                  }

                  final String contextPath = (String) context.getProperties().get(HYSTRIX_METRICS_PATH);
                  final String url = getMetricsStreamUrl(contextPath);
                  log.info("Deploying Hystrix metrics stream at {}", url);

                  http.deployServlet(contextPath, SERVLET_NAME, Collections.singletonList(createServletDescriptor()));

                  if (log.isTraceEnabled()) {
                     log.trace("Waiting for Hystrix metrics stream to appear at {}", url);
                  }

                  if (!Utils.waitForHttp(url, 200)) {
                     throw new InterruptedException("Unable to start Hystrix metrics stream.");
                  }
               }
            }

            Thread.sleep(1000);
         }
      } catch (InterruptedException ex) {
         Utils.shutdownLog(log, ex);
      } catch (Exception ex) {
         log.error("Hystrix microservice provider failed: ", ex);
      }
   }

   private ServletDescriptor createServletDescriptor() {
      return new ServletDescriptor(SERVLET_NAME, HystrixMetricsStreamServlet.class, "/", new Properties());
   }

   private String getMetricsStreamUrl(String contextPath) {
      Object server = context.getProperties().get(HttpServerSilverService.HTTP_SERVER_ADDRESS);
      Object port = context.getProperties().get(HttpServerSilverService.HTTP_SERVER_PORT);
      return String.format("http://%s:%s/%s", server, port, contextPath);
   }

   private boolean isMetricsEnabled() {
      return Boolean.parseBoolean((String) context.getProperties().get(HYSTRIX_METRICS_ENABLED));
   }
}
