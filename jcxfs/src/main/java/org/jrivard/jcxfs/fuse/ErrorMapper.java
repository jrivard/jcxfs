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

package org.jrivard.jcxfs.fuse;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.cryptomator.jfuse.api.Errno;
import org.jrivard.jcxfs.xodusfs.FileOpError;

public enum ErrorMapper {
    NO_SUCH_DIR(FileOpError.NO_SUCH_DIR, Errno::enoent),
    NO_SUCH_FILE(FileOpError.NO_SUCH_FILE, Errno::enoent),
    NOT_A_DIRECTORY(FileOpError.NOT_A_DIRECTORY, Errno::enotdir),
    NOT_A_FILE(FileOpError.NOT_A_FILE, Errno::eisdir),
    DIR_NOT_EMPTY(FileOpError.DIR_NOT_EMPTY, Errno::enotempty),
    IO_ERROR(FileOpError.IO_ERROR, Errno::eio),
    FILE_EXISTS(FileOpError.FILE_EXISTS, Errno::eexist),
    ;

    private static final Set<ErrorMapper> ALL_ERRORMAPPERS = EnumSet.allOf(ErrorMapper.class);

    private final FileOpError fileOpError;
    private final Function<Errno, Integer> errnoIntegerFunction;

    ErrorMapper(final FileOpError fileOpError, final Function<Errno, Integer> errnoIntegerFunction) {
        this.fileOpError = fileOpError;
        this.errnoIntegerFunction = errnoIntegerFunction;
    }

    static Optional<ErrorMapper> forFileOpError(final FileOpError fileOpError) {
        return ALL_ERRORMAPPERS.stream()
                .filter(e -> fileOpError == e.fileOpError)
                .findAny();
    }

    public int errno(final Errno errno) {
        return errnoIntegerFunction.apply(errno);
    }
}
