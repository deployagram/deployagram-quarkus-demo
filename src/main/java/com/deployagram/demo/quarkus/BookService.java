package com.deployagram.demo.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class BookService {
    @RestClient
    ClassicBookClient classicBookClient;

    @Inject
    MyMessagingApplication myMessagingApplication;

    BookSuggestion recommend(String email) {
        BookSuggestion book = classicBookClient.getBook();
        DontForget dontForget = new DontForget(email, book);
        myMessagingApplication.sendBookSuggestion(dontForget);
        return book;
    }
}
