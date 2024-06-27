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

public class RuntimeXodusFsException extends RuntimeException {
    private final FileOpError fileOpError;

    private RuntimeXodusFsException(final FileOpError fileOpError, final String message) {
        super(message);

        this.fileOpError = fileOpError;
    }

    public static RuntimeXodusFsException of(final FileOpError fileOpError, final String message) {
        return new RuntimeXodusFsException(fileOpError, message);
    }

    public FileOpException asXodusFsException() {
        final FileOpException fileOpException = FileOpException.of(fileOpError, this.getMessage());
        return fileOpException;
    }
}