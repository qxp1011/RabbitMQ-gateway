package io.scalecube.gateway.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.io.IOException;

public class RabbitListener {
  private final ConnectionFactory factory;
  final Connection connection;
  final Channel channel;
  private final Subject<byte[], byte[]> incomingMessagesSubject;
  private MessageSerialization serialization;

  public RabbitListener(String host, int port, int timeout, String password) throws Exception {
    this.factory = new ConnectionFactory();

    this.factory.setHost(host);

    if (port != -1) {
      this.factory.setPort(port);
    }

    this.factory.setConnectionTimeout(timeout);

    if (password != null) {
      this.factory.setPassword(password);
    }

    this.connection = factory.newConnection();
    this.channel = connection.createChannel();
    this.incomingMessagesSubject = PublishSubject.<byte[]>create().toSerialized();
  }

  public void subscribe(Topic topic) throws Exception {
    channel.queueDeclare(topic.name(),
        topic.durable(),
        topic.exclusive(),
        topic.autoDelete(), null);

    final Consumer consumer = new DefaultConsumer(channel) {

      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
          throws IOException {
        try {
          incomingMessagesSubject.onNext(body);
        } catch (Exception e) {
          e.printStackTrace();
        }
        try {
        } finally {
          channel.basicAck(envelope.getDeliveryTag(), false);
        }
      }
    };

    boolean autoAck = false;
    channel.basicConsume(topic.name(), autoAck, consumer);
  }

  @SuppressWarnings("unchecked")
  public <T> Observable<T> listen(MessageSerialization serialization, Class<T> class1) {
    this.serialization = serialization;
    return (Observable<T>) incomingMessagesSubject
        .onBackpressureBuffer().map(onNext -> deserialize((byte[]) onNext, class1));
  }

  public <T> Observable<byte[]> listen() {
    return incomingMessagesSubject.onBackpressureBuffer();
  }
  
  private <T> T deserialize(byte[] onNext, Class<T> class1) {
    try {
      return this.serialization.deserialize(onNext, class1);
    } catch (Exception e) {
      return null;
    }
  }

  public Channel channel() {
    return this.channel;
  }

  
}