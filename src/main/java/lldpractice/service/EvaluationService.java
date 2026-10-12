package lldpractice.service;

import java.util.List;
import java.util.concurrent.Executor;

import lldpractice.design.DesignModel;
import lldpractice.design.ExtractorRegistry;
import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Feedback;
import lldpractice.domain.Problem;
import lldpractice.domain.exceptions.InvalidTransitionException;
import lldpractice.domain.exceptions.NotFoundException;
import lldpractice.evaluation.EvaluationContext;
import lldpractice.evaluation.EvaluationPipeline;
import lldpractice.evaluation.EvaluationPipeline.PipelineResult;
import lldpractice.evaluation.FeedbackBuilder;
import lldpractice.repository.AttemptRepository;
import lldpractice.repository.ProblemRepository;

/**
 * Runs an attempt's evaluation on an injected {@link Executor}: a real thread pool in the app,
 * a same-thread executor in tests so results are deterministic and immediately observable.
 *
 * <p>Double-scheduling is prevented by the attempt's own state machine: moving to
 * {@link AttemptStatus#EVALUATING} is a single compare-and-swap, so if two callers race (e.g.
 * submit and a stray retry), only one of them actually schedules work — the other's
 * {@link InvalidTransitionException} is swallowed as a no-op.
 */
public final class EvaluationService {

    private final ProblemRepository problemRepository;
    private final AttemptRepository attemptRepository;
    private final ExtractorRegistry extractorRegistry;
    private final List<lldpractice.evaluation.Evaluator> evaluators;
    private final FeedbackBuilder feedbackBuilder = new FeedbackBuilder();
    private final Executor executor;

    public EvaluationService(ProblemRepository problemRepository, AttemptRepository attemptRepository,
                              ExtractorRegistry extractorRegistry, List<lldpractice.evaluation.Evaluator> evaluators,
                              Executor executor) {
        this.problemRepository = problemRepository;
        this.attemptRepository = attemptRepository;
        this.extractorRegistry = extractorRegistry;
        this.evaluators = List.copyOf(evaluators);
        this.executor = executor;
    }

    /** Schedules (or re-schedules, for a retry) evaluation of this attempt. Safe to call more
     * than once concurrently: only the caller that wins the status transition actually runs it. */
    public void evaluateAsync(Attempt attempt) {
        try {
            attempt.transitionTo(AttemptStatus.EVALUATING);
        } catch (InvalidTransitionException e) {
            return; // already evaluating, or in a state that can't start evaluation; nothing to do
        }
        attemptRepository.save(attempt);
        executor.execute(() -> runEvaluation(attempt));
    }

    private void runEvaluation(Attempt attempt) {
        try {
            Problem problem = problemRepository.findById(attempt.problemId())
                    .orElseThrow(() -> new NotFoundException("No such problem: " + attempt.problemId()));
            DesignModel designModel = extractorRegistry.forFormat(attempt.submission().format())
                    .extract(attempt.submission());
            EvaluationContext context = new EvaluationContext(problem, attempt.submission(), designModel);

            EvaluationPipeline pipeline = new EvaluationPipeline(evaluators);
            PipelineResult result = pipeline.run(context);
            Feedback feedback = feedbackBuilder.build(problem, result);

            attempt.completeWith(feedback, !result.degraded());
        } catch (RuntimeException e) {
            attempt.failWith(describe(e));
        } finally {
            attemptRepository.save(attempt);
        }
    }

    private String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
