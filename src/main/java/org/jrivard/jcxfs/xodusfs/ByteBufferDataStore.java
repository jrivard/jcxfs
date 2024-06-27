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
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteBufferByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.ByteIterator;
import jetbrains.exodus.bindings.LongBinding;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.JcxfsException;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.LogUtil;
import org.jrivard.jcxfs.util.JavaUtil;
import org.jrivard.jcxfs.util.JcxfsOutput;

public class ByteBufferDataStore implements DataStore {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(ByteBufferDataStore.class);

    private final EnvironmentWrapper environmentWrapper;
    private final Store dataStore;
    private final Store dataLengthStore;
    private final int pageSize;

    public ByteBufferDataStore(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
        this.environmentWrapper = environmentWrapper;
        this.dataStore = environmentWrapper.getStore(XodusStore.DATA);
        this.dataLengthStore = environmentWrapper.getStore(XodusStore.DATA_LENGTH);
        this.pageSize = environmentWrapper.readXodusFsParams().orElseThrow().pageSize();
    }

    private void writeFidLength(final Transaction txn, final long fid, final long length) {
        dataLengthStore.put(txn, LongBinding.longToEntry(fid), LongBinding.longToEntry(length));
    }

    private long readFidLength(final Transaction txn, final long fid) {
        final ByteIterable storedValue = dataLengthStore.get(txn, LongBinding.longToEntry(fid));
        return storedValue == null ? 0 : LongBinding.entryToLong(storedValue);
    }

    @Override
    public long totalPagesUsed(final Transaction txn) {
        return dataStore.count(txn);
    }

    @Override
    public long length(final Transaction txn, final long fid) {
        return readFidLength(txn, fid);
    }

    @Override
    public void truncate(final Transaction txn, final long id, final long length) {
        final long existingLength = readFidLength(txn, id);

        if (existingLength < 0) {
            throw new IllegalStateException("no such fid");
        }

        if (existingLength <= length) {
            return;
        }

        final int newLastPage = Math.toIntExact(Math.divideExact(length, pageSize));

        {
            final int newLastPageEndPosition = Math.toIntExact(length % pageSize);
            if (newLastPageEndPosition > 0) {
                final ByteIterable pageData = readPage(txn, id, newLastPage);
                if (pageData.getLength() > newLastPageEndPosition) {
                    final ByteBuffer newPageData = ByteBuffer.allocate(newLastPageEndPosition);
                    final ByteIterator iter = pageData.iterator();
                    while (iter.hasNext() && newPageData.hasRemaining()) {
                        newPageData.put(iter.next());
                    }
                    newPageData.flip();
                    writePage(txn, id, newLastPage, newPageData);
                }
            }
        }

        {
            final int existingTotalPages = calculateTotalDataPages(existingLength);
            for (int loopPage = newLastPage + 1; loopPage <= existingTotalPages; loopPage++) {
                final ByteIterable key = DataKey.toByteIterable(id, loopPage);
                dataStore.delete(txn, key);
            }
        }

        LOGGER.trace(() -> "truncated id=" + InodeId.prettyPrint(id) + " new length=" + length);
        writeFidLength(txn, id, length);
    }

    public void deleteEntry(final Transaction txn, final long fid) {
        final long totalLength = readFidLength(txn, fid);
        if (totalLength < 0) {
            throw new IllegalStateException("no such fid");
        }

        final int totalPages = calculateTotalDataPages(totalLength);

        if (totalPages >= 0) {
            for (int loopPage = 0; loopPage <= totalPages; loopPage++) {
                final ByteIterable key = DataKey.toByteIterable(fid, loopPage);
                dataStore.delete(txn, key);
            }
        }

        dataLengthStore.delete(txn, LongBinding.longToEntry(fid));
        LOGGER.trace(() -> "removed " + (totalPages + 1) + " pages for fid " + fid);
    }

    public int readData(
            final Transaction txn, final long fid, final ByteBuffer buf, final long count, final long offset) {
        final long storedLength = readFidLength(txn, fid);
        final long requestedLastPosition = offset + count;

        final long recalculatedCount;
        if (requestedLastPosition > storedLength) {
            recalculatedCount = storedLength - offset;
            LOGGER.trace(() -> "read requested beyond file length, requestedLast=" + requestedLastPosition
                    + " storedLast=" + storedLength);
        } else {
            recalculatedCount = count;
        }

        return readData2(txn, fid, buf, recalculatedCount, offset);
    }

    private int readData2(
            final Transaction txn, final long fid, final ByteBuffer tempData, final long count, final long offset) {
        final long firstPosition = offset;
        final long lastPosition = offset + count;

        long position = offset;

        final int firstPage = Math.toIntExact(Math.divideExact(offset, pageSize));
        int page = firstPage;
        int bytesCopied = 0;

        while (position < lastPosition) {
            final ByteIterable currentPageData = readPage(txn, fid, page);

            final int totalBytesRemaining = Math.toIntExact(Math.subtractExact(lastPosition, position));
            final int pageReadStart = Math.toIntExact(position % pageSize);
            // final int pageReadLength = Math.min( pageSize - pageReadStart, totalBytesRemaining - pageReadStart );
            final int pageReadLength;
            {
                int tempPageReadLength = pageSize - pageReadStart;
                if (tempPageReadLength > totalBytesRemaining) {
                    tempPageReadLength = totalBytesRemaining;
                }
                pageReadLength = tempPageReadLength;
            }

            final int pageReadEnd = pageReadStart + pageReadLength;

            if (LOGGER.isLevel(LogUtil.LogLevel.trace)) {
                final long position_finalCopy = position;
                LOGGER.trace(() -> "firstPos=" + firstPosition
                        + " lastPosition=" + lastPosition
                        + " position=" + position_finalCopy
                        + " pageReadStart=" + pageReadStart
                        + " pageReadEnd=" + pageReadStart
                        + " pageReadStart=" + pageReadStart
                        + " pageReadLength=" + pageReadLength
                        + " totalBytesRemaining=" + totalBytesRemaining);
            }

            {
                final int effectiveEndPosition = Math.min(pageReadEnd, currentPageData.getLength());
                final int copyLength = Math.max(0, effectiveEndPosition - pageReadStart);
                if (copyLength > 0) {
                    final ByteIterator slice = currentPageData
                            .subIterable(pageReadStart, copyLength)
                            .iterator();
                    while (slice.hasNext()) {
                        tempData.put(slice.next());
                    }
                    position += copyLength;
                    bytesCopied += copyLength;
                }

                final int padBytes = pageReadLength - copyLength;
                if (padBytes > 0) {
                    for (int i = 0; i < padBytes; i++) {
                        tempData.put((byte) 0x00);
                    }
                    position += padBytes;
                    bytesCopied += padBytes;
                }
            }

            page++;
        }

        return bytesCopied;
    }

    public int writeData(
            final Transaction txn, final long fid, final ByteBuffer buf, final long count, final long offset) {
        final long firstPos = offset;
        final long lastPosition = offset + count;

        long position = offset;

        final int firstPage = Math.toIntExact(Math.divideExact(offset, pageSize));
        final int lastPage = Math.toIntExact(firstPage + Math.divideExact(count, pageSize));
        int page = firstPage;

        int bytesWritten = 0;

        while (buf.hasRemaining()) {
            final int pageWriteStart = Math.toIntExact(position % pageSize);
            final int pageWriteEnd = Math.min(Math.addExact(pageWriteStart, buf.remaining()), pageSize);
            final int pageWriteLength = pageWriteEnd - pageWriteStart;

            final ByteBuffer nextWriteSlice = buf.slice(buf.position(), pageWriteLength);
            buf.position(buf.position() + pageWriteLength);

            final ByteBuffer pageOutput;
            if (pageWriteStart != 0 || pageWriteEnd != pageSize) {
                // not writing full page, so first read existing page into output buffer
                final ByteIterable existingPageData = readPage(txn, fid, page);
                pageOutput = ByteBuffer.allocate(Math.max(pageWriteEnd, existingPageData.getLength()));
                for (final ByteIterator iterator = existingPageData.iterator(); iterator.hasNext(); ) {
                    pageOutput.put(iterator.next());
                }
                // pageOutput.flip();
                // pageOutput.position( pageWriteStart );
                pageOutput.put(nextWriteSlice);
                pageOutput.flip();
            } else {
                pageOutput = nextWriteSlice;
            }

            // write argument buffer to page output buffer
            // buf.get(pageOutput, pageWriteStart, pageWriteLength);

            writePage(txn, fid, page, pageOutput);
            position += pageWriteLength;

            page++;
            bytesWritten += pageWriteLength;
        }

        updateLengthIfNeeded(txn, fid, lastPosition);

        return bytesWritten;
    }

    private void updateLengthIfNeeded(final Transaction txn, final long fid, final long newLength) {
        final long storedLength = readFidLength(txn, fid);
        if (newLength > storedLength) {
            writeFidLength(txn, fid, newLength);
            LOGGER.trace(() ->
                    "setlength inode=" + InodeId.prettyPrint(fid) + " length=" + newLength + " old=" + storedLength);
        }
    }

    private ByteIterable readPage(final Transaction txn, final long fid, final int page) {
        final ByteIterable blockKey = DataKey.toByteIterable(fid, page);
        final ByteIterable valueIterable = dataStore.get(txn, blockKey);
        return valueIterable == null ? ByteIterable.EMPTY : valueIterable;
        // final byte[] data = valueIterable == null ? new byte[0] : valueIterable.getBytesUnsafe();
        // logPageOperation("read ", fid, page, data);
        // return data;
    }

    private void writePage(final Transaction txn, final long fid, final int page, final ByteBuffer data) {
        final ByteIterable blockKey = DataKey.toByteIterable(fid, page);
        final int lastNonNullByte = JavaUtil.suffixNullCount(data);

        final ByteIterable valueIterable;
        if (lastNonNullByte == 0) {
            valueIterable = new ByteBufferByteIterable(data);
        } else {
            final int length = data.limit() - lastNonNullByte;
            if (data.hasArray()) {
                valueIterable = new ArrayByteIterable(data.array(), length);
            } else {
                final byte[] byteArray = new byte[length];
                data.get(byteArray, 0, length);
                valueIterable = new ArrayByteIterable(byteArray);
            }
        }
        logPageOperation("write", fid, page, data::array);
        dataStore.put(txn, blockKey, valueIterable);
    }

    private void logPageOperation(final String prefix, final long fid, final int page, final Supplier<byte[]> data) {
        if (!LOGGER.isLevel(LogUtil.LogLevel.trace)) {
            return;
        }

        final var byteArray = data.get();
        final int suffixNulls = JavaUtil.suffixNullCount(byteArray);

        final StringBuilder msg =
                new StringBuilder(JavaUtil.padRight(prefix, 6, ' ').substring(0, 6)
                        + " inode=" + InodeId.prettyPrint(fid)
                        + " page=" + HexFormat.of().toHexDigits(page)
                        + " suffixNulls=" + suffixNulls
                        + " length=" + HexFormat.of().toHexDigits(byteArray.length));

        if (byteArray.length > 0) {
            msg.append(" last=").append(HexFormat.of().toHexDigits(byteArray[byteArray.length - 1]));
        }
        LOGGER.trace(msg::toString);
    }

    private int calculateTotalDataPages(final long length) {
        return Math.toIntExact(Math.divideExact(length, pageSize));
    }

    private class DebugOutputter {

        private final Transaction transaction;
        private final JcxfsOutput output;

        public DebugOutputter(final Transaction transaction, final JcxfsOutput output) {
            this.transaction = transaction;
            this.output = output;
        }

        public void printStats() {
            environmentWrapper
                    .allEntries(transaction, XodusStore.DATA)
                    .forEach(entry -> printPathEntryDebug(entry, output));
        }

        private void printPathEntryDebug(final Map.Entry<ByteIterable, ByteIterable> entry, final JcxfsOutput writer) {
            final DataKey dataKey = DataKey.fromByteIterable(entry.getKey());
            writer.writeLine(" dataPage: inode=" + InodeId.prettyPrint(dataKey.fid()) + " page: " + dataKey.page()
                    + " length: " + entry.getValue().getLength());
        }

        static Map.Entry<PathKey, Long> convertPathMapEntry(final Map.Entry<ByteIterable, ByteIterable> entry) {
            return Map.entry(PathKey.fromByteIterable(entry.getKey()), InodeId.byteIterableToInodeId(entry.getValue()));
        }
    }

    public void printStats(final JcxfsOutput writer) {

        try {
            environmentWrapper.doExecute(txn -> {
                new DebugOutputter(txn, writer).printStats();
            });
        } catch (final FileOpException e) {
            throw new RuntimeException(e);
        }
    }
}
