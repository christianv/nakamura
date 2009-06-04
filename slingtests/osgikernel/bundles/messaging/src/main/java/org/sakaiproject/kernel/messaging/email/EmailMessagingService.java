/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.kernel.messaging.email;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.mail.Email;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.kernel.api.jcr.JCRService;
import org.sakaiproject.kernel.api.messaging.MessageConverter;
import org.sakaiproject.kernel.api.messaging.MessagingConstants;
import org.sakaiproject.kernel.api.messaging.MessagingException;
import org.sakaiproject.kernel.api.messaging.email.CommonsEmailHandler;
import org.sakaiproject.kernel.api.user.UserFactoryService;
import org.sakaiproject.kernel.messaging.JcrMessagingService;
import org.sakaiproject.kernel.messaging.email.commons.HtmlEmail;
import org.sakaiproject.kernel.messaging.email.commons.MultiPartEmail;
import org.sakaiproject.kernel.messaging.email.commons.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

public class EmailMessagingService extends JcrMessagingService implements CommonsEmailHandler {
  private static final Logger LOG = LoggerFactory.getLogger(EmailMessagingService.class);
  private Long clientId = Long.valueOf(1L);

  /** @off-scr.property value="vm://localhost?broker.persistent=true" */
  private static final String JMS_BROKER_URL = MessagingConstants.JMS_BROKER_URL;

  /** @off-scr.property value="kernel.jms.email;" */
  private static final String JMS_EMAIL_TYPE = MessagingConstants.JMS_EMAIL_TYPE;

  /** @off-scr.property value="kernel.email;" */
  private static final String JMS_EMAIL_QUEUE = MessagingConstants.JMS_EMAIL_TYPE;

  private String emailJmsType;
  private String emailQueueName;
  private ActiveMQConnectionFactory connectionFactory;
  private ArrayList<Connection> connections = new ArrayList<Connection>();
  private ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<String, Session>();

  synchronized public long getClientId() {
    return clientId;
  }

  synchronized public void setClientId(long id) {
    this.clientId = id;
  }

  synchronized private String getNextId() {
    setClientId(getClientId() + 1);
    return "" + getClientId();
  }

  @SuppressWarnings("unchecked")
  public void activate(ComponentContext ctx) {
    Dictionary dict = ctx.getProperties();
    String brokerUrl = (String) dict.get(JMS_BROKER_URL);
    connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
    try {
      // prob want to use username,pw here
      Connection conn = connectionFactory.createTopicConnection();
      conn.setClientID("kernel.email1");
      connections.add(conn);
    } catch (JMSException e) {
      connectionFactory = null;
      try {
        throw e;
      } catch (JMSException e1) {
        e1.printStackTrace();
      }
    }

    startConnections();
    createSessions();
  }

  /*
   * TODO may want to take parameters for num of connections and sessions per
   * connection
   */
  public EmailMessagingService(MessageConverter msgConverter, UserFactoryService userFactory,
      JCRService jcr) {
    super(msgConverter, userFactory, jcr);
  }

  public void setConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public void setConnectionFactory(ConnectionFactory connectionFactory) {
    this.connectionFactory = (ActiveMQConnectionFactory) connectionFactory;
  }

  public ConnectionFactory getConnectionFactory() {
    return connectionFactory;
  }

  private void startConnections() {
    for (Connection conn : connections) {
      try {
        conn.start();
      } catch (JMSException e) {
        try {
          LOG.error("Fail to start connection: {}", conn.getClientID());
        } catch (JMSException e1) {
          // TMI
        }
        e.printStackTrace();
      }
    }

  }

  private void createSessions() {
    int sessionsPerConnection = 1;
    Session sess = null;
    for (Connection conn : connections) {
      for (int i = 0; i < sessionsPerConnection; ++i) {
        sess = null;
        try {
          sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
          sessions.put(conn.getClientID(), sess);
        } catch (JMSException e) {
          try {
            LOG.error("Fail to create connection[{}]: {}", i, conn.getClientID());
          } catch (JMSException e1) {
            // TMI
          }
          e.printStackTrace();
        }
      }
    }

  }

  public String send(Email email) throws MessagingException {
    try {
      email.buildMimeMessage();
    } catch (Exception e) {
      // this is a lossy cast. This would be a commons EmailException
      // this up cast is to keep commons-email out of our direct bindings
      throw new MessagingException(e);
    }

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    try {
      email.getMimeMessage().writeTo(os);
      String content = os.toString();
      Connection conn = connectionFactory.createTopicConnection();
      conn.setClientID(getNextId());
      Session clientSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination emailTopic = clientSession.createTopic(emailQueueName);
      MessageProducer client = clientSession.createProducer(emailTopic);
      ObjectMessage mesg = clientSession.createObjectMessage(content);
      mesg.setJMSType(emailJmsType);
      client.send(mesg);
      // TODO finish this
      return null;
    } catch (javax.mail.MessagingException e) {
      throw new MessagingException(e);
    } catch (IOException e) {
      throw new MessagingException(e);
    } catch (JMSException e) {
      throw new MessagingException(e.getMessage(), e);
    }

  }

  public HtmlEmail createHtmlEmail() {
    return new HtmlEmail(this);
  }

  public MultiPartEmail createMultiPartEmail() {
    return new MultiPartEmail(this);
  }

  public SimpleEmail createSimpleEmail() {
    return new SimpleEmail(this);
  }

}
