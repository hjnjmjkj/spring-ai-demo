package com.epgis.sggis.mcp.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    @Tool(description = "执行精确的两数四则运算。适用于加法、减法、乘法和除法；不要自行估算数学结果。")
    public CalculationResult calculate(
            @ToolParam(description = "运算类型，只能是 ADD、SUBTRACT、MULTIPLY 或 DIVIDE")
            CalculationOperation operation,
            @ToolParam(description = "左操作数") BigDecimal left,
            @ToolParam(description = "右操作数") BigDecimal right) {

        Objects.requireNonNull(operation, "operation 不能为空");
        Objects.requireNonNull(left, "left 不能为空");
        Objects.requireNonNull(right, "right 不能为空");

        BigDecimal result = switch (operation) {
            case ADD -> left.add(right);
            case SUBTRACT -> left.subtract(right);
            case MULTIPLY -> left.multiply(right);
            case DIVIDE -> divide(left, right);
        };

        String expression = left.toPlainString() + " " + operation.symbol() + " " + right.toPlainString();
        return new CalculationResult(operation, left, right, expression, result.stripTrailingZeros());
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("除数不能为零");
        }
        return left.divide(right, DIVISION_CONTEXT);
    }
}
