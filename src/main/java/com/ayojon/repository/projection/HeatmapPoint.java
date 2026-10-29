package com.ayojon.repository.projection;

import java.math.BigDecimal;

/** F2: one weighted point for the heat map. */
public interface HeatmapPoint {
    Long getVenueId();
    String getVenueName();
    String getAreaName();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    Long getVisitCount();
}
