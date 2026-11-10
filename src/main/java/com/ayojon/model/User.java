package com.ayojon.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record User(int id, String name, String email, String role, Integer points,
                   @JsonIgnore String passwordHash) {}
