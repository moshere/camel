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
package org.apache.camel.spring.interceptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;

/**
 * @version $Revision$
 */
public class TransactionalClientDataSourceTransactedWithFileLocalOnExceptionTest extends TransactionalClientDataSourceTransactedWithFileOnExceptionTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {

                from("file://target/transacted/okay")
                    .transacted()
                    .setBody(constant("Tiger in Action")).beanRef("bookService")
                    .setBody(constant("Elephant in Action")).beanRef("bookService");

                from("file://target/transacted/fail?moveFailed=../failed")
                    .transacted()
                    .onException(IllegalArgumentException.class)
                        .handled(false)
                        .to("mock:error")
                    .end()
                    .setBody(constant("Tiger in Action")).beanRef("bookService")
                    .setBody(constant("Donkey in Action")).beanRef("bookService");
            }
        };
    }

}