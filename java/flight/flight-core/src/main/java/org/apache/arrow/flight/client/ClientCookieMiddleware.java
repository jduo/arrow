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

package org.apache.arrow.flight.client;

import java.net.HttpCookie;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.flight.CallInfo;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightClientMiddleware;
import org.apache.arrow.util.VisibleForTesting;

/**
 * A client middleware for receiving and sending cookie information.
 * Note that this class will not persist permanent cookies beyond the lifetime
 * of this session.
 */
public class ClientCookieMiddleware implements FlightClientMiddleware {
  private static final String SET_COOKIE_HEADER = "Set-Cookie";
  private static final String COOKIE_HEADER = "Cookie";
  private final Factory factory;

  public ClientCookieMiddleware(Factory factory) {
    this.factory = factory;
  }

  /**
   * Factory used within FlightClient.
   */
  public static class Factory implements FlightClientMiddleware.Factory {
    // Use a map to track the most recent version of a cookie from the server.
    // Note that cookie names are case-sensitive (but header names aren't).
    private ConcurrentMap<String, HttpCookie> cookies = new ConcurrentHashMap<>();

    @Override
    public ClientCookieMiddleware onCallStarted(CallInfo info) {
      return new ClientCookieMiddleware(this);
    }
  }

  @Override
  public void onBeforeSendingHeaders(CallHeaders outgoingHeaders) {
    final String cookieValue = getValidCookiesAsString();
    if (!cookieValue.isEmpty()) {
      outgoingHeaders.insert(COOKIE_HEADER, cookieValue);
    }
  }

  @Override
  public void onHeadersReceived(CallHeaders incomingHeaders) {
    // Note: A cookie defined once will continue to be used in all subsequent
    // requests on the client instance. The server can send the same cookie again
    // with a different value and the client will use the new value in future requests.
    // The server can also update a cookie to have an Expiry in the past or negative age
    // to signal that the client should stop using the cookie immediately.
    final Consumer<String> handleSetCookieHeader = (headerValue) -> {
      final List<HttpCookie> parsedCookies = HttpCookie.parse(headerValue);
      parsedCookies.forEach(parsedCookie -> factory.cookies.put(parsedCookie.getName(), parsedCookie));
    };
    final Iterable<String> setCookieHeaders = incomingHeaders.getAll(SET_COOKIE_HEADER);
    if (setCookieHeaders != null) {
      for (String cookieHeader : setCookieHeaders) {
        handleSetCookieHeader.accept(cookieHeader);
      }
    }
  }

  @Override
  public void onCallCompleted(CallStatus status) {

  }

  /**
   * Discards expired cookies and returns the valid cookies as a String delimited by ';'.
   */
  @VisibleForTesting
  String getValidCookiesAsString() {
    // Discard expired cookies.
    factory.cookies.entrySet().removeIf(cookieEntry -> cookieEntry.getValue().hasExpired());

    // Cookie header value format:
    // [<cookie-name1>=<cookie-value1>; <cookie-name2>=<cookie-value2; ...]
    return factory.cookies.entrySet().stream()
        .map(cookie -> cookie.getValue().toString())
        .collect(Collectors.joining("; "));
  }
}
