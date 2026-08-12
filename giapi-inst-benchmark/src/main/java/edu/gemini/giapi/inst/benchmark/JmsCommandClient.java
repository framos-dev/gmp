package edu.gemini.giapi.inst.benchmark;

import edu.gemini.aspen.giapi.commands.Command;
import edu.gemini.aspen.giapi.commands.CompletionInformation;
import edu.gemini.aspen.giapi.commands.HandlerResponse;
import edu.gemini.aspen.giapi.util.jms.HandlerResponseMessageParser;
import edu.gemini.aspen.giapi.util.jms.JmsKeys;
import edu.gemini.aspen.giapi.util.jms.MessageBuilder;
import edu.gemini.aspen.giapi.util.jms.MessageBuilderFactory;
import edu.gemini.jms.api.MapMessageBuilder;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.UUID;

/**
 * Sends GIAPI command messages with one reusable JMS connection.
 */
final class JmsCommandClient implements AutoCloseable {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final Destination commandTopic;
    private final Destination replyQueue;

    JmsCommandClient(String brokerUrl) throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setOptimizeAcknowledge(true);
        factory.setAlwaysSessionAsync(false);

        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        commandTopic = session.createTopic(JmsKeys.GW_COMMAND_TOPIC);
        replyQueue = session.createQueue(JmsKeys.GW_COMMAND_REPLY_QUEUE);
        producer = session.createProducer(commandTopic);
    }

    CommandSample send(Command command, long timeout, int sampleNo) throws JMSException {
        String correlationID = UUID.randomUUID().toString();
        MessageConsumer replyConsumer = session.createConsumer(
                replyQueue,
                "JMSCorrelationID='" + correlationID + "'");
        try {
            long startNanos = System.nanoTime();
            sendRequest(command, correlationID);
            HandlerResponse initialResponse = readInitialResponse(replyConsumer, timeout);
            long initialNanos = System.nanoTime() - startNanos;

            HandlerResponse finalResponse = initialResponse;
            long endToEndNanos = initialNanos;
            boolean started = HandlerResponse.STARTED.equals(initialResponse);
            if (started) {
                CompletionInformation completionInformation = readCompletion(replyConsumer, timeout);
                finalResponse = completionInformation.getHandlerResponse();
                endToEndNanos = System.nanoTime() - startNanos;
            }
            return new CommandSample(sampleNo, finalResponse, initialNanos, endToEndNanos, started);
        } finally {
            replyConsumer.close();
        }
    }

    private void sendRequest(Command command, String correlationID) throws JMSException {
        MapMessage request = session.createMapMessage();
        MapMessageBuilder messageBuilder = MessageBuilderFactory.newMessageBuilder(command, correlationID);
        producer.send(messageBuilder.constructMessageBody(request));
    }

    private HandlerResponse readInitialResponse(MessageConsumer consumer, long timeout) throws JMSException {
        Message reply = consumer.receive(timeout);
        if (reply == null) {
            return HandlerResponse.createError("Initial response not received in " + timeout + " [ms]");
        }
        return new HandlerResponseMessageParser(reply).readResponse();
    }

    private CompletionInformation readCompletion(MessageConsumer consumer, long timeout) throws JMSException {
        Message reply = consumer.receive(timeout);
        if (reply == null) {
            return new CompletionInformation(
                    HandlerResponse.createError("Completion response not received in " + timeout + " [ms]"),
                    Command.noCommand());
        }
        return MessageBuilder.buildCompletionInformation(reply);
    }

    @Override
    public void close() throws JMSException {
        try {
            producer.close();
        } finally {
            try {
                session.close();
            } finally {
                connection.close();
            }
        }
    }
}
