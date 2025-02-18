/*
 * Copyright (2023) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.delta.kernel.utils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

import io.delta.kernel.annotation.Evolving;

import io.delta.kernel.internal.util.Utils;

/**
 * Closeable extension of {@link Iterator}
 *
 * @param <T> the type of elements returned by this iterator
 * @since 3.0.0
 */
@Evolving
public interface CloseableIterator<T> extends Iterator<T>, Closeable {
    default <U> CloseableIterator<U> map(Function<T, U> mapper) {
        CloseableIterator<T> delegate = this;
        return new CloseableIterator<U>() {
            @Override
            public void remove() {
                delegate.remove();
            }

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public U next() {
                return mapper.apply(delegate.next());
            }

            @Override
            public void close()
                throws IOException {
                delegate.close();
            }
        };
    }

    default CloseableIterator<T> filter(Function<T, Boolean> mapper) {
        CloseableIterator<T> delegate = this;
        return new CloseableIterator<T>() {
            T next;
            boolean hasLoadedNext;

            @Override
            public boolean hasNext() {
                if (hasLoadedNext) {
                    return true;
                }
                while (delegate.hasNext()) {
                    T potentialNext = delegate.next();
                    if (mapper.apply(potentialNext)) {
                        next = potentialNext;
                        hasLoadedNext = true;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                hasLoadedNext = false;
                return next;
            }

            @Override
            public void close()
                throws IOException {
                delegate.close();
            }
        };
    }

    /**
     * Combine the current iterator with another iterator. The resulting iterator will return all
     * elements from the current iterator followed by all elements from the other iterator.
     *
     * @param other the other iterator to combine with
     * @return a new iterator that combines the current iterator with the other iterator
     */
    default CloseableIterator<T> combine(CloseableIterator<T> other) {

        CloseableIterator<T> delegate = this;
        return new CloseableIterator<T>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext() || other.hasNext();
            }

            @Override
            public T next() {
                if (delegate.hasNext()) {
                    return delegate.next();
                } else {
                    return other.next();
                }
            }

            @Override
            public void close() throws IOException {
                Utils.closeCloseables(delegate, other);
            }
        };
    }
}
