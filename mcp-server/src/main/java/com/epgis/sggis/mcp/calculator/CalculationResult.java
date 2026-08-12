package com.epgis.sggis.mcp.calculator;

import java.math.BigDecimal;

public record CalculationResult(
        CalculationOperation operation,
        BigDecimal left,
        BigDecimal right,
        String expression,
        BigDecimal result) {
}
