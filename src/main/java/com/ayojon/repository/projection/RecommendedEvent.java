package com.ayojon.repository.projection;

import java.math.BigDecimal;

/** F19: event card plus its recommendation score (higher is better). */
public interface RecommendedEvent extends EventCard {
    BigDecimal getScore();
}
