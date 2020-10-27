/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.arrow.flight.auth2;

import java.util.Optional;

import org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.flight.FlightRuntimeException;

/**
 * Interface for Server side authentication handlers.
 */
public interface CallHeaderAuthenticator {
  /**
   * The result of the server analyzing authentication headers.
   */
  interface AuthResult {
    /**
     * The peer identity that was determined by the handshake process based on the
     * authentication credentials supplied by the client.
     *
     * @return The peer identity.
     */
    String getPeerIdentity();

    /**
     * The bearer token that was generated by the handshake process if applicable.
     *
     * @return bearer token, or Optional.empty() if bearer tokens are not supported by the auth mechanism.
     */
    default Optional<String> getBearerToken() {
      return Optional.empty();
    }

    /**
     * Appends a header to the outgoing headers.
     *
     * @param outgoingHeaders The outgoing headers to append the headers to.
     */
    void appendToOutgoingHeaders(CallHeaders outgoingHeaders);
  }

  /**
   * Validate the auth headers sent by the client.
   *
   * @param headers The headers to authenticate.
   * @return a handshake result containing a peer identity and optionally a bearer token.
   * @throws FlightRuntimeException with CallStatus.UNAUTHENTICATED if credentials were not supplied
   *     or CallStatus.UNAUTHORIZED if credentials were supplied but were not valid.
   */
  AuthResult authenticate(CallHeaders headers);

  /**
   * Validate a bearer token.
   *
   * @param bearerToken The token to validate.
   * @return true if the given token is valid.
   */
  boolean validateBearer(String bearerToken);

  /**
   * An auth handler that does nothing.
   */
  CallHeaderAuthenticator NO_OP = new CallHeaderAuthenticator() {
    @Override
    public AuthResult authenticate(CallHeaders headers) {
      return new AuthResult() {
        @Override
        public String getPeerIdentity() {
          return "";
        }

        @Override
        public Optional<String> getBearerToken() {
          return Optional.empty();
        }

        @Override
        public void appendToOutgoingHeaders(CallHeaders outgoingHeaders) {

        }
      };
    }

    @Override
    public boolean validateBearer(String bearerToken) {
      return true;
    }

  };
}
