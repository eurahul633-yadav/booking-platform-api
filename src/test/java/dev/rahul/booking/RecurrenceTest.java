package dev.rahul.booking;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class RecurrenceTest {
  @Test
  void weeklySeriesPreservesLocalTimeAcrossDst() {
    var starts =
        SchedulingService.weekly(Instant.parse("2027-03-21T08:00:00Z"), "Europe/Vilnius", 3, 1);
    assertThat(starts)
        .containsExactly(
            Instant.parse("2027-03-21T08:00:00Z"),
            Instant.parse("2027-03-28T07:00:00Z"),
            Instant.parse("2027-04-04T07:00:00Z"));
  }

  @Test
  void missingOrAmbiguousLocalTimeRejectsSeries() {
    assertThatThrownBy(
            () ->
                SchedulingService.weekly(
                    Instant.parse("2027-03-21T01:30:00Z"), "Europe/Vilnius", 2, 1))
        .isInstanceOf(ApiFailure.class);
    assertThatThrownBy(
            () ->
                SchedulingService.weekly(
                    Instant.parse("2027-10-24T00:30:00Z"), "Europe/Vilnius", 2, 1))
        .isInstanceOf(ApiFailure.class);
  }
}
