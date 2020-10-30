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

package org.apache.arrow.flight;

import org.apache.arrow.util.Preconditions;

/**
 * A producer that adds a "close" Action and delegates to another producer for all other Actions.
 */
public class DelegatingCloseProducer implements FlightProducer {
  final FlightProducer producer;
  final CloseHandler closeHandler;

  /**
   * Create a close producer from a delegate and the handler for the close Action.
   * @param producer The delegate producer.
   * @param closeHandler The handler for any close Actions.
   */
  public DelegatingCloseProducer(FlightProducer producer, CloseHandler closeHandler) {
    Preconditions.checkNotNull(producer);
    Preconditions.checkNotNull(closeHandler);
    this.producer = producer;
    this.closeHandler = closeHandler;
  }

  @Override
  public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener) {
    this.producer.getStream(context, ticket, listener);
  }

  @Override
  public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener) {
    this.producer.listFlights(context, criteria, listener);
  }

  @Override
  public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor) {
    return this.producer.getFlightInfo(context, descriptor);
  }

  @Override
  public SchemaResult getSchema(CallContext context, FlightDescriptor descriptor) {
    return this.producer.getSchema(context, descriptor);
  }

  @Override
  public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream) {
    return this.producer.acceptPut(context, flightStream, ackStream);
  }

  @Override
  public void doExchange(CallContext context, FlightStream reader, ServerStreamListener writer) {
    this.producer.doExchange(context, reader, writer);
  }

  @Override
  public void doAction(CallContext context, Action action, StreamListener<Result> listener) {
    switch (action.getType()) {
      case "close": {
        closeHandler.close();
        listener.onCompleted();
        break;
      }
      default: {
        this.producer.doAction(context, action, listener);
      }
    }
  }

  @Override
  public void listActions(CallContext context, StreamListener<ActionType> listener) {
    listener.onNext(new ActionType("close", "close resources associated with the session."));
    this.producer.listActions(context, listener);
  }
}
