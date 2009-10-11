/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.git4idea.ssh;

import org.jetbrains.annotations.Nullable;

import java.util.Vector;

/**
 * An interface for GIT SSH handler
 */
public interface GitSSHHandler {
  /**
   * Verify server host key
   *
   * @param handler                  a handler identifier
   * @param hostName                 a host name
   * @param port                     a port number
   * @param serverHostKeyAlgorithm   an algorithm
   * @param serverHostKeyFingerprint a key fingerprint
   * @param isNew                    true if the key is a new, false if the key was changed
   * @return true the host is verified, false otherwise
   */
  boolean verifyServerHostKey(int handler,
                              String hostName,
                              int port,
                              String serverHostKeyAlgorithm,
                              String serverHostKeyFingerprint,
                              boolean isNew);

  /**
   * Ask passphrase for the key
   *
   * @param handler   a handler identifier
   * @param userName  a name of user
   * @param keyPath   a path for the key
   * @param lastError a last error (or empty string)
   * @return the passphrase entered by the user
   */
  @Nullable
  String askPassphrase(final int handler, final String userName, final String keyPath, final String lastError);

  /**
   * Reply to challenge for keyboard-interactive method. Also used for
   *
   * @param handlerNo   a handler identifier
   * @param userName    a user name (includes host and port)
   * @param name        name of challenge
   * @param instruction instruction
   * @param numPrompts  amount of prompts
   * @param prompt      prompts
   * @param echo        whether the reply should be echoed (boolean values represented as string due to XML RPC limitation)
   * @param lastError   @return answers provided by the user
   * @return a list or replies to challenges (the size should be equal to the number of prompts)
   */
  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  @Nullable
  Vector<String> replyToChallenge(final int handlerNo,
                                  final String userName,
                                  final String name,
                                  final String instruction,
                                  final int numPrompts,
                                  final Vector<String> prompt,
                                  final Vector<Boolean> echo,
                                  final String lastError);

  /**
   * Ask password for the specified user name
   *
   * @param handlerNo a handler identifier
   * @param userName  a name of user to ask password for
   * @param lastError a last error
   * @return the password or null if authentication failed.
   */
  @Nullable
  String askPassword(final int handlerNo, final String userName, final String lastError);
}
