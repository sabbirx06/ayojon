package com.ayojon.model;

public class EventNotEditableException extends RuntimeException {
    public EventNotEditableException(int eventId) {
        super("Event " + eventId + " is no longer a draft and cannot be edited.");
    }
}