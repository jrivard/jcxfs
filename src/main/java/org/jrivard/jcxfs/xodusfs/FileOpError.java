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

import java.util.function.Function;
import org.cryptomator.jfuse.api.Errno;

public enum FileOpError {
    NO_SUCH_DIR(Errno::enoent),
    NO_SUCH_FILE(Errno::enoent),
    NOT_A_DIRECTORY(Errno::enotdir),
    NOT_A_FILE(Errno::eisdir),
    DIR_NOT_EMPTY(Errno::enotempty),
    IO_ERROR(Errno::eio),
    FILE_EXISTS(Errno::eexist),
    ;

    private final Function<Errno, Integer> errnoIntegerFunction;

    FileOpError(final Function<Errno, Integer> errnoIntegerFunction) {
        this.errnoIntegerFunction = errnoIntegerFunction;
    }

    public int errno(final Errno errno) {
        return errnoIntegerFunction.apply(errno);
    }
}
