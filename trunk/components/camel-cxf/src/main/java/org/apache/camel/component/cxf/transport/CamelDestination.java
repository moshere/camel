/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.util.CxfHeaderHelper;
import org.apache.camel.component.cxf.util.CxfMessageHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl.EndpointReferenceUtils;

/**
 * @version $Revision$
 */
public class CamelDestination extends AbstractDestination implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-destination";
    private static final Logger LOG = LogUtils.getL7dLogger(CamelDestination.class);
    final ConduitInitiator conduitInitiator;
    CamelContext camelContext;
    Consumer consumer;
    String camelDestinationUri;

    private Endpoint destinationEndpoint;
    private HeaderFilterStrategy headerFilterStrategy;
    private boolean checkException;

    public CamelDestination(CamelContext camelContext, Bus bus, ConduitInitiator ci, EndpointInfo info) throws IOException {
        this(camelContext, bus, ci, info, null, false);
    }
    
    public CamelDestination(CamelContext camelContext, Bus bus, ConduitInitiator ci, EndpointInfo info,
            HeaderFilterStrategy headerFilterStrategy, boolean checkException) throws IOException {
        super(bus, getTargetReference(info, bus), info);
        this.camelContext = camelContext;
        conduitInitiator = ci;
        camelDestinationUri = endpointInfo.getAddress().substring(CxfConstants.CAMEL_TRANSPORT_PREFIX.length());
        if (camelDestinationUri.startsWith("//")) {
            camelDestinationUri = camelDestinationUri.substring(2);
        }
        initConfig();
        this.headerFilterStrategy = headerFilterStrategy;
        this.checkException = checkException;
    }

    protected Logger getLogger() {
        return LOG;
    }

    public void setCheckException(boolean exception) {
        checkException = exception;
    }
    
    public boolean isCheckException() {
        return checkException;
    }
    /**
     * @param inMessage the incoming message
     * @return the inbuilt backchannel
     */
    protected Conduit getInbuiltBackChannel(Message inMessage) {
        //we can pass the message back by looking up the camelExchange from inMessage
        return new BackChannelConduit(inMessage);
    }

    public void activate() {
        getLogger().log(Level.FINE, "CamelDestination activate().... ");
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        try {
            getLogger().log(Level.FINE, "establishing Camel connection");
            destinationEndpoint = getCamelContext().getEndpoint(camelDestinationUri);
            consumer = destinationEndpoint.createConsumer(new ConsumerProcessor());
            ServiceHelper.startService(consumer);
        } catch (Exception ex) {
            throw new FailedToCreateConsumerException(destinationEndpoint, ex);
        }
    }

    public void deactivate() {
        try {
            ServiceHelper.stopService(consumer);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error stopping consumer", e);
        }
    }

    public void shutdown() {
        getLogger().log(Level.FINE, "CamelDestination shutdown()");
        this.deactivate();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    protected void incoming(org.apache.camel.Exchange camelExchange) {
        getLogger().log(Level.FINE, "server received request: ", camelExchange);
        org.apache.cxf.message.Message inMessage =
            CxfMessageHelper.getCxfInMessage(headerFilterStrategy, camelExchange, false);

        inMessage.put(CxfConstants.CAMEL_EXCHANGE, camelExchange);
        ((MessageImpl)inMessage).setDestination(this);

        // Handling the incoming message
        // The response message will be send back by the outgoing chain
        incomingObserver.onMessage(inMessage);
    }

    public String getBeanName() {
        if (endpointInfo == null || endpointInfo.getName() == null) {
            return "default" + BASE_BEAN_NAME_SUFFIX;
        }
        return endpointInfo.getName().toString() + BASE_BEAN_NAME_SUFFIX;
    }
    
    public String getCamelDestinationUri() {
        return camelDestinationUri;
    }

    private void initConfig() {
        //we could configure the camel context here
        if (bus != null) {
            Configurer configurer = bus.getExtension(Configurer.class);
            if (null != configurer) {
                configurer.configureBean(this);
            }
        }
    }

    protected class ConsumerProcessor implements Processor {
        public void process(Exchange exchange) {
            try {
                incoming(exchange);
            } catch (Throwable ex) {
                getLogger().log(Level.WARNING, "Failed to process incoming message: ", ex);
            }
        }
    }

    // this should deal with the cxf message
    protected class BackChannelConduit extends AbstractConduit {
        protected Message inMessage;
        Exchange camelExchange;
        org.apache.cxf.message.Exchange cxfExchange;
        BackChannelConduit(Message message) {
            super(EndpointReferenceUtils.getAnonymousEndpointReference());
            inMessage = message;
            cxfExchange = inMessage.getExchange();
            camelExchange = cxfExchange.get(Exchange.class);
        }

        /**
         * Register a message observer for incoming messages.
         *
         * @param observer the observer to notify on receipt of incoming
         */
        public void setMessageObserver(MessageObserver observer) {
            // shouldn't be called for a back channel conduit
        }

        /**
         * Send an outbound message, assumed to contain all the name-value
         * mappings of the corresponding input message (if any).
         *
         * @param message the message to be sent.
         */
        public void prepare(Message message) throws IOException {
            message.put(CxfConstants.CAMEL_EXCHANGE, inMessage.get(CxfConstants.CAMEL_EXCHANGE));
            message.setContent(OutputStream.class, new CamelOutputStream(message));
        }

        protected Logger getLogger() {
            return LOG;
        }

    }

    /**
     * Mark message as a partial message.
     *
     * @param partialResponse the partial response message
     * @param decoupledTarget the decoupled target
     * @return <tt>true</tt> if partial responses is supported
     */
    protected boolean markPartialResponse(Message partialResponse,
                                          EndpointReferenceType decoupledTarget) {
        return true;
    }

    /**
     * @return the associated conduit initiator
     */
    protected ConduitInitiator getConduitInitiator() {
        return conduitInitiator;
    }

    protected void propagateResponseHeadersToCamel(Message outMessage, Exchange camelExchange) {
        CxfHeaderHelper.propagateCxfToCamel(headerFilterStrategy, outMessage, 
                                            camelExchange.getOut().getHeaders(), camelExchange);            
    }

    private class CamelOutputStream extends CachedOutputStream {
        private Message outMessage;

        public CamelOutputStream(Message m) {
            super();
            outMessage = m;
        }

        // Prepare the message and get the send out message
        private void commitOutputMessage() throws IOException {
            Exchange camelExchange = (Exchange)outMessage.get(CxfConstants.CAMEL_EXCHANGE);
            
            propagateResponseHeadersToCamel(outMessage, camelExchange);
            
            // check if the outMessage has the exception
            Exception exception = outMessage.getContent(Exception.class);
            if (checkException && exception != null) {
                camelExchange.setException(exception);
            }
            
            CachedOutputStream outputStream = (CachedOutputStream)outMessage.getContent(OutputStream.class);
            camelExchange.getOut().setBody(outputStream.getBytes());
            getLogger().log(Level.FINE, "send the response message: " + outputStream);
        }

        @Override
        protected void doFlush() throws IOException {
            // Do nothing here
        }

        @Override
        protected void doClose() throws IOException {
            commitOutputMessage();
        }

        @Override
        protected void onWrite() throws IOException {
            // Do nothing here
        }
    }

}
