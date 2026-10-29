package com.subshift.backend.ml;

import com.subshift.backend.common.AppTime;
import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.pricelog.PriceLog;
import com.subshift.backend.pricelog.PriceLogRepository;
import com.subshift.backend.subscriptions.Subscription;
import com.subshift.backend.subscriptions.SubscriptionRepository;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Forecasts a subscription's NEXT price change (how much, and roughly when) from its own price history.
 * The model is trained only on real data (the price_log table plus the bundled real price histories) and
 * evaluated with grouped cross-validation against simple baselines (see PriceModelEvaluator).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceForecastService {

    /** Capped at the number of services in the evaluator, so at this data size it is leave-one-service-out. */
    private static final int FOLDS = 100;
    private static final long SEED = 42;
    private static final int MIN_TRANSITIONS = 20;

    public record ForecastResponse(Integer subscriptionId, String serviceName, BigDecimal currentPrice,
                                   BigDecimal predictedNextPrice, Double expectedIncreasePercent,
                                   Double expectedMonthsUntilChange, LocalDate expectedChangeDate,
                                   int priceChangesObserved, String note) {}

    private record Trained(PriceForecastModel model, PriceForecastMetrics metrics) {}

    private final PriceTrainingData trainingData;
    private final SubscriptionRepository subscriptionRepository;
    private final PriceLogRepository priceLogRepository;
    private final CurrentUserService currentUserService;
    private volatile Trained trained;

    @EventListener(ApplicationReadyEvent.class)
    public void trainOnStartup() {
        try {
            PriceForecastMetrics metrics = train();
            log.info("Price forecast model trained on {} real examples from {} price histories",
                    metrics.transitions(), metrics.series());
        } catch (ResponseStatusException e) {
            log.info("Price forecast model not trained at startup: {}", e.getReason());
        } catch (Exception e) {
            log.warn("Price forecast training failed at startup", e);
        }
    }

    public synchronized PriceForecastMetrics train() {
        List<PriceSeries> series = trainingData.loadAll();
        List<PriceFeatures.Transition> transitions = PriceFeatures.transitions(series);
        if (transitions.size() < MIN_TRANSITIONS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Need at least " + MIN_TRANSITIONS + " price-change examples to train (have " + transitions.size() + ")");
        }
        PriceModelEvaluator.Evaluation evaluation = PriceModelEvaluator.crossValidate(transitions, FOLDS, SEED);
        PriceForecastModel model = new PriceForecastModel();
        model.fit(transitions);

        List<PriceModelEvaluator.Score> scores = evaluation.scores().stream().map(PriceForecastService::rounded).toList();
        PriceForecastMetrics metrics = new PriceForecastMetrics(Instant.now(), series.size(), transitions.size(),
                evaluation.folds(), scores, comparisonNote(scores, transitions.size(), series.size()));
        trained = new Trained(model, metrics);
        return metrics;
    }

    /** Says plainly whether Ridge beat the "Average increase" baseline, so small-data results are not oversold. */
    private static String comparisonNote(List<PriceModelEvaluator.Score> scores, int transitions, int series) {
        PriceModelEvaluator.Score ridge = scores.get(0);
        PriceModelEvaluator.Score average = scores.stream()
                .filter(s -> s.name().equals("Average increase")).findFirst().orElse(scores.get(scores.size() - 1));
        boolean beats = ridge.rmseGrowthPoints() < average.rmseGrowthPoints() && ridge.maeMonths() < average.maeMonths();
        return "Trained on " + transitions + " real price-change examples from " + series
                + " price histories, tested leave-one-service-out. Ridge growth error " + ridge.rmseGrowthPoints()
                + " points vs " + average.rmseGrowthPoints() + " for the average-increase baseline; timing error "
                + ridge.maeMonths() + " vs " + average.maeMonths() + " months. "
                + (beats ? "Ridge beats the baseline on both."
                        : "Ridge does not beat the simple average baseline yet, so treat forecasts as rough averages.");
    }

    public Optional<PriceForecastMetrics> metrics() {
        Trained current = trained;
        return current == null ? Optional.empty() : Optional.of(current.metrics());
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecast(String email, Integer subscriptionId) {
        User user = currentUserService.require(email);
        // Someone else's subscription looks exactly like a missing one: 404, not 403.
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        String serviceName = subscription.getService().getName();

        List<LocalDate> dates = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        for (PriceLog entry : priceLogRepository.findBySubscriptionIdOrderByDateRecordedAscIdAsc(subscriptionId)) {
            double price = entry.getPrice().doubleValue();
            if (price > 0 && (prices.isEmpty() || prices.get(prices.size() - 1) != price)) {
                dates.add(entry.getDateRecorded());
                prices.add(price);
            }
        }
        int observed = Math.max(0, prices.size() - 1);
        BigDecimal current = prices.isEmpty() ? null : BigDecimal.valueOf(prices.get(prices.size() - 1));

        Trained model = trained;
        if (prices.size() < 2) {
            return unavailable(subscriptionId, serviceName, current, observed,
                    "A forecast needs at least two recorded prices for this subscription.");
        }
        if (model == null) {
            return unavailable(subscriptionId, serviceName, current, observed, "The forecast model is not trained yet.");
        }

        PriceSeries series = new PriceSeries("forecast", subscription.getService().getCategory().getName(), dates, prices);
        PriceForecastModel.Prediction prediction = model.model().predict(PriceFeatures.features(series, prices.size() - 1));
        double ratio = Math.exp(prediction.growth());
        BigDecimal predicted = current.multiply(BigDecimal.valueOf(ratio)).setScale(0, RoundingMode.HALF_UP);

        LocalDate lastChange = dates.get(dates.size() - 1);
        LocalDate expected = lastChange.plusDays(Math.round(prediction.months() * 30.4375));
        String note = "Rough estimate from this subscription's price history and the typical gaps and increases seen across services.";
        if (expected.isBefore(AppTime.today())) {
            expected = AppTime.today().plusDays(30);
            note = "A change looks overdue compared with the usual gap. Rough estimate from this subscription's price "
                    + "history and the typical gaps and increases seen across services.";
        }
        return new ForecastResponse(subscriptionId, serviceName, current, predicted,
                round1((ratio - 1.0) * 100.0), round1(prediction.months()), expected, observed, note);
    }

    private static ForecastResponse unavailable(Integer id, String serviceName, BigDecimal current, int observed, String note) {
        return new ForecastResponse(id, serviceName, current, null, null, null, null, observed, note);
    }

    private static PriceModelEvaluator.Score rounded(PriceModelEvaluator.Score s) {
        return new PriceModelEvaluator.Score(s.name(), round(s.rmseRupees()), round(s.maeRupees()),
                round(s.rmseGrowthPoints()), round(s.maeMonths()));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
