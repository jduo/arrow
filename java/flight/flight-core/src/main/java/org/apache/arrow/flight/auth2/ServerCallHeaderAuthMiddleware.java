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

import org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.flight.CallInfo;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightServerMiddleware;
import org.apache.arrow.flight.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Middleware that's used to validate credentials during the handshake and verify
 * the bearer token in subsequent requests.
 */
public class ServerCallHeaderAuthMiddleware implements FlightServerMiddleware {
  private static final Logger logger = LoggerFactory.getLogger(ServerCallHeaderAuthMiddleware.class);

  /**
   * Factory for accessing ServerAuthMiddleware.
   */
  public static class Factory implements FlightServerMiddleware.Factory<ServerCallHeaderAuthMiddleware> {
    private final CallHeaderAuthenticator authHandler;
    private final GeneratedBearerTokenAuthHandler bearerTokenAuthHandler;

    /**
     * Construct a factory with the given auth handler.
     *
     * @param authHandler The auth handler what will be used for authenticating requests.
     */
    public Factory(CallHeaderAuthenticator authHandler) {
      this.authHandler = authHandler;
      this.bearerTokenAuthHandler = authHandler.enableCachedCredentials() ?
              new GeneratedBearerTokenAuthHandler() : null;
    }

    @Override
    public ServerCallHeaderAuthMiddleware onCallStarted(CallInfo callInfo, CallHeaders incomingHeaders,
                                                        RequestContext context) {
      // Check if bearer token auth is being used, and if we've enabled use of server-generated
      // bearer tokens.
      if (authHandler.enableCachedCredentials()) {
        final String bearerToken = bearerTokenAuthHandler.getBearerToken(incomingHeaders, context);
        if (bearerToken != null) {
          return new ServerCallHeaderAuthMiddleware(bearerToken, bearerTokenAuthHandler);
        }
      }

      // Delegate to server auth handler to do the validation.
      final CallHeaderAuthenticator.AuthResult result = authHandler.authenticate(incomingHeaders);
      context.put(Auth2Constants.PEER_IDENTITY_KEY, result.getPeerIdentity());
      if (authHandler.enableCachedCredentials()) {
        return new ServerCallHeaderAuthMiddleware(
                bearerTokenAuthHandler.registerBearer(result), bearerTokenAuthHandler);
      } else {
        return new ServerCallHeaderAuthMiddleware(result.getBearerToken().get(), authHandler);
      }
    }
  }

  private final String bearerToken;
  private final CallHeaderAuthenticator authHandler;

  public ServerCallHeaderAuthMiddleware(String bearerToken, CallHeaderAuthenticator authHandler) {
    this.bearerToken = bearerToken;
    this.authHandler = authHandler;
  }

  @Override
  public void onBeforeSendingHeaders(CallHeaders outgoingHeaders) {
    authHandler.addBearerTokenToOutgoingHeaderIfNotPresent(outgoingHeaders, bearerToken);
  }

  @Override
  public void onCallCompleted(CallStatus status) {
    logger.debug("Call completed with status {}", status);
  }

  @Override
  public void onCallErrored(Throwable err) {
    logger.error("Call failed", err);
  }
}
