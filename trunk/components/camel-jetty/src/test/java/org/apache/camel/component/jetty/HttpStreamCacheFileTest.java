/**
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
package org.apache.camel.component.jetty;

import java.io.File;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpOperationFailedException;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class HttpStreamCacheFileTest extends CamelTestSupport {

    private String body = "12345678901234567890123456789012345678901234567890";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("./target/cachedir");
        createDirectory("./target/cachedir");
        super.setUp();
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfResponse() throws Exception {
        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        // give time for files to be deleted etc.
        Thread.sleep(2000);

        // the temporary files should have been deleted
        File file = new File("./target/cachedir");
        String[] files = file.list();
        assertEquals("There should be no files", 0, files.length);
    }

    @Test
    public void testStreamCacheToFileShouldBeDeletedInCaseOfException() throws Exception {
        try {
            template.requestBody("direct:start", null, String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException hofe = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            String s = context.getTypeConverter().convertTo(String.class, hofe.getResponseBody());
            assertEquals("Response body", body, s);
        }

        // give time for files to be deleted etc.
        Thread.sleep(2000);

        // the temporary files should have been deleted
        File file = new File("./target/cachedir");
        String[] files = file.list();
        assertEquals("There should be no files", 0, files.length);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable stream caching and use a low threshold so its forced to write to file
                context.getProperties().put(CachedOutputStream.TEMP_DIR, "./target/cachedir");
                context.getProperties().put(CachedOutputStream.THRESHOLD, "16");
                context.setStreamCaching(true);

                // use a route so we got an unit of work
                from("direct:start").to("http://localhost:8123/myserver");

                from("jetty://http://localhost:8123/myserver")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                if (exchange.getIn().getBody() == null) {
                                    exchange.getOut().setBody("12345678901234567890123456789012345678901234567890");
                                    exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                                } else {
                                    exchange.getOut().setBody("Bye World");
                                }
                            }
                        });
            }
        };
    }

}
