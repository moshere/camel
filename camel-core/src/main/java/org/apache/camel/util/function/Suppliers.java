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
package org.apache.camel.util.function;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class Suppliers {
    private Suppliers() {
    }

    public static <T> Supplier<T> memorize(Supplier<T> supplier) {
        final AtomicReference<T> valueHolder = new AtomicReference<>();
        return () -> {
            T supplied = valueHolder.get();
            if (supplied == null) {
                synchronized (valueHolder) {
                    supplied = valueHolder.get();
                    if (supplied == null) {
                        supplied = Objects.requireNonNull(supplier.get(), "Supplier should not return null");
                        valueHolder.lazySet(supplied);
                    }
                }
            }
            return supplied;
        };
    }

    public static <T> Optional<T> firstNotNull(ThrowingSupplier<T, Exception>... suppliers) throws Exception {
        T answer = null;

        for (int i = 0; i < suppliers.length; i++) {
            answer = suppliers[i].get();
            if (answer != null) {
                break;
            }
        }

        return Optional.ofNullable(answer);
    }
}
