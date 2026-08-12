package com.epgis.sggis.mcp.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CalculatorServiceTest {

    private final CalculatorService calculator = new CalculatorService();

    @Test
    void addsDecimalsExactly() {
        CalculationResult result = calculator.calculate(
                CalculationOperation.ADD, new BigDecimal("0.1"), new BigDecimal("0.2"));

        assertThat(result.result()).isEqualByComparingTo("0.3");
        assertThat(result.expression()).isEqualTo("0.1 + 0.2");
    }

    @Test
    void subtractsNegativeNumbers() {
        CalculationResult result = calculator.calculate(
                CalculationOperation.SUBTRACT, new BigDecimal("-3"), new BigDecimal("2"));

        assertThat(result.result()).isEqualByComparingTo("-5");
    }

    @Test
    void multipliesNumbers() {
        CalculationResult result = calculator.calculate(
                CalculationOperation.MULTIPLY, new BigDecimal("123.45"), new BigDecimal("67.8"));

        assertThat(result.result()).isEqualByComparingTo("8369.91");
    }

    @Test
    void dividesWithDecimal128Precision() {
        CalculationResult result = calculator.calculate(
                CalculationOperation.DIVIDE, BigDecimal.ONE, new BigDecimal("3"));

        assertThat(result.result().toPlainString()).startsWith("0.3333333333333333");
    }

    @Test
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> calculator.calculate(
                CalculationOperation.DIVIDE, BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("除数不能为零");
    }

    @Test
    void rejectsMissingOperation() {
        assertThatThrownBy(() -> calculator.calculate(null, BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operation 不能为空");
    }
}
