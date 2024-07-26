/*
 * Copyright 2024 Jason D. Rivard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jrivard.jcxfs.xodusfs;

import java.nio.ByteBuffer;
import jetbrains.exodus.env.Transaction;

interface DataStore extends StoreBucket {
    enum DataStoreImplType {
        byte_array,
        byte_buffer,
        ;

        public DataStore makeImpl(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
            return switch (this) {
                case byte_buffer -> new ByteBufferDataStore(environmentWrapper);
                case byte_array -> new ByteArrayDataStore(environmentWrapper);
            };
        }
    }

    long length(Transaction txn, long fid);

    void truncate(Transaction txn, long id, long length);

    void deleteEntry(Transaction txn, long nodeId);

    int readData(Transaction txn, long nodeId, ByteBuffer outputBuffer, long count, long offset);

    int writeData(Transaction txn, long nodeId, ByteBuffer inputBuffer, long count, long offset);

    long totalPagesUsed(Transaction txn);
}
