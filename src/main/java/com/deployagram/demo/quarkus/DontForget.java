package com.deployagram.demo.quarkus;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record DontForget(String email, BookSuggestion suggestion) { }
