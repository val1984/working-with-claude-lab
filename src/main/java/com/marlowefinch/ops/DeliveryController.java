package com.marlowefinch.ops;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 500;

    private final DashboardRepository repository;
    private final Clock clock;

    public DeliveryController(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/api/deliveries/on-time")
    public List<CarrierOnTime> onTime(@RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        return repository.onTimeByCarrier(DateRange.resolve(from, to, clock));
    }

    @GetMapping("/api/deliveries/late")
    public List<LateDelivery> late(@RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to,
                                   @RequestParam(required = false) String limit) {
        List<String> errors = new ArrayList<>();
        DateRange range = DateRange.resolve(from, to, clock, errors);
        int rows = parseLimit(limit, errors);
        if (!errors.isEmpty()) {
            throw new InvalidParametersException(errors);
        }
        return repository.lateDeliveries(range, rows);
    }

    /** Bound as a String so that {@code limit=abc} gets the same 400 shape as any other bad value. */
    private static int parseLimit(String limit, List<String> errors) {
        if (limit == null || limit.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(limit.trim());
            if (value >= 1 && value <= MAX_LIMIT) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        errors.add("limit must be an integer between 1 and " + MAX_LIMIT);
        return DEFAULT_LIMIT;
    }
}
