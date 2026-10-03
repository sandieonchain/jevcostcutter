package io.jevopt.shadow;
import io.jevopt.core.*;
import java.math.BigDecimal;
import java.util.Optional;
/** Evidence only: implementations cannot mutate production or return authorization. */
public interface ShadowEvaluator {
 String version();
 Optional<ShadowResult> evaluate(RuntimeSample sample, BigDecimal threshold);
}

