package com.ayojon.repository.projection;

/** F1: event card plus distance from the user's location. */
public interface NearbyEvent extends EventCard {
    Double getDistanceKm();
}
