package com.deployagram.demo.quarkus;

import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class MyMessagingApplication {

    @Inject
    @Channel("suggestions")
    @Broadcast
    Emitter<DontForget> bookEmitter;

    void sendBookSuggestion(DontForget message) {
        bookEmitter.send(message);
    }
}
