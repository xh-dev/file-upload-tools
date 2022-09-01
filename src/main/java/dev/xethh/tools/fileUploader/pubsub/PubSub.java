package dev.xethh.tools.fileUploader.pubsub;

import com.solacesystems.jcsmp.*;
import io.vavr.control.Try;
import me.xethh.utils.functionalPacks.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Component
public class PubSub {
    private String url;
    private String un;
    private String pwd;
    private String vpn;

    public PubSub(
            @Value("${dev.xethh.tools.file-uploader.pubsub.url}") String url,
            @Value("${dev.xethh.tools.file-uploader.pubsub.un}") String un,
            @Value("${dev.xethh.tools.file-uploader.pubsub.pwd}") String pwd,
            @Value("${dev.xethh.tools.file-uploader.pubsub.vpn}") String vpn) {
        this.url = url;
        this.un = un;
        this.pwd = pwd;
        this.vpn = vpn;
    }

    private JCSMPProperties createProperty() {
        return Scope.apply(new JCSMPProperties(), properties -> {
            properties.setProperty(JCSMPProperties.HOST, url);
            properties.setProperty(JCSMPProperties.USERNAME, un);
            properties.setProperty(JCSMPProperties.PASSWORD, pwd);
            properties.setProperty(JCSMPProperties.VPN_NAME, vpn);
        });
    }

    private Try<JCSMPSession> createSession() {
        return Try.of(() -> {
                    JCSMPSession session = JCSMPFactory.onlyInstance().createSession(createProperty());
                    session.connect();
                    return session;
                }
        );
    }

    JCSMPSession session;

    @PostConstruct
    public void init() {
        session = createSession().get();
        Sinks.Many<String> flux = Sinks.many().multicast().directBestEffort();
        Flux<String> outFlux = producer(flux.asFlux());
        Flux.range(0, 10)
                .delayElements(Duration.of(10, ChronoUnit.SECONDS))
                .map(it -> Try.of(() -> flux.tryEmitNext(String.format("%02d", it))))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        outFlux
                .map(it -> {
                    System.out.println(String.format("Received: %s", it));
                    return it;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    Flux<String> producer(Flux<String> flux) {
        Sinks.Many<String> out = Sinks.many().multicast().directBestEffort();

        Queue queue = JCSMPFactory.onlyInstance().createQueue("topic/test");
        Topic topic = JCSMPFactory.onlyInstance().createTopic("topic/xeth/message1");

        Try<FlowReceiver> consumer = Try.of(() -> {
            ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
            flow_prop.setEndpoint(queue);
            return session.createFlow(new XMLMessageListener() {
                @Override
                public void onReceive(BytesXMLMessage bytesXMLMessage) {
                    if(bytesXMLMessage instanceof TextMessage){
                        out.tryEmitNext(((TextMessage) bytesXMLMessage).getText());
                    } else {
                        out.tryEmitNext("Unknown");
                    }

                }

                @Override
                public void onException(JCSMPException e) {
                    e.printStackTrace();
                    out.tryEmitError(e);
                }
            }, flow_prop);
        });


        Try.of(()->{
            consumer.get().start();
            return consumer;
        }).get();

        XMLMessageProducer prod = Try.of(() -> {
            return session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {

                @Override
                public void responseReceivedEx(Object o) {
                    System.out.println("Producer received response for msg: " + o);
                }

                @Override
                public void handleErrorEx(Object o, JCSMPException e, long l) {
                    System.out.printf("Producer received error for msg: %s@%s - %s%n",
                            o, l, e);
                }

            });
        }).get();
        flux
                .map(it -> {
                    System.out.println("Item: " + it);
                    return it;
                })
                .map(it ->
                        Try.of(() -> {
                            TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
                            msg.setText(it);
                            prod.send(msg, topic);
                            return it;
                        }).get())
                .subscribeOn(Schedulers.boundedElastic()).subscribe();

        return out.asFlux();
    }
}
