package com.example.linuxsimulator;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.util.Stack;

public class CalculatorActivity extends Activity implements View.OnClickListener {

    private TextView tvDisplay, tvPreviousOperation;
    private String currentNumber = "0";
    private String previousOperation = "";
    private boolean isNewOperation = true;
    private boolean hasDecimal = false;
    private double memory = 0;
    private DecimalFormat df = new DecimalFormat("0.##########");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        initializeViews();
        setClickListeners();
    }

    private void initializeViews() {
        tvDisplay = findViewById(R.id.tvDisplay);
        tvPreviousOperation = findViewById(R.id.tvPreviousOperation);
    }

    private void setClickListeners() {
        // Numbers
        findViewById(R.id.btn0).setOnClickListener(this);
        findViewById(R.id.btn1).setOnClickListener(this);
        findViewById(R.id.btn2).setOnClickListener(this);
        findViewById(R.id.btn3).setOnClickListener(this);
        findViewById(R.id.btn4).setOnClickListener(this);
        findViewById(R.id.btn5).setOnClickListener(this);
        findViewById(R.id.btn6).setOnClickListener(this);
        findViewById(R.id.btn7).setOnClickListener(this);
        findViewById(R.id.btn8).setOnClickListener(this);
        findViewById(R.id.btn9).setOnClickListener(this);

        // Basic Operations
        findViewById(R.id.btnAdd).setOnClickListener(this);
        findViewById(R.id.btnSubtract).setOnClickListener(this);
        findViewById(R.id.btnMultiply).setOnClickListener(this);
        findViewById(R.id.btnDivide).setOnClickListener(this);
        findViewById(R.id.btnEquals).setOnClickListener(this);
        findViewById(R.id.btnDecimal).setOnClickListener(this);

        // Scientific Operations
        findViewById(R.id.btnSin).setOnClickListener(this);
        findViewById(R.id.btnCos).setOnClickListener(this);
        findViewById(R.id.btnTan).setOnClickListener(this);
        findViewById(R.id.btnLog).setOnClickListener(this);
        findViewById(R.id.btnLn).setOnClickListener(this);
        findViewById(R.id.btnSqrt).setOnClickListener(this);
        findViewById(R.id.btnPower).setOnClickListener(this);
        findViewById(R.id.btnSquare).setOnClickListener(this);
        findViewById(R.id.btnFactorial).setOnClickListener(this);
        findViewById(R.id.btnInverse).setOnClickListener(this);
        findViewById(R.id.btnAbs).setOnClickListener(this);

        // Constants
        findViewById(R.id.btnPi).setOnClickListener(this);
        findViewById(R.id.btnE).setOnClickListener(this);

        // Memory Functions
        findViewById(R.id.btnMC).setOnClickListener(this);
        findViewById(R.id.btnMR).setOnClickListener(this);
        findViewById(R.id.btnMPlus).setOnClickListener(this);

        // Utility Functions
        findViewById(R.id.btnClear).setOnClickListener(this);
        findViewById(R.id.btnBackspace).setOnClickListener(this);
        findViewById(R.id.btnPlusMinus).setOnClickListener(this);
        findViewById(R.id.btnPercent).setOnClickListener(this);
        findViewById(R.id.btnOpenParen).setOnClickListener(this);
        findViewById(R.id.btnCloseParen).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btn0 || id == R.id.btn1 || id == R.id.btn2 ||
                id == R.id.btn3 || id == R.id.btn4 || id == R.id.btn5 ||
                id == R.id.btn6 || id == R.id.btn7 || id == R.id.btn8 ||
                id == R.id.btn9) {
            handleNumberInput(((Button) v).getText().toString());
        } else if (id == R.id.btnDecimal) {
            handleDecimal();
        } else if (id == R.id.btnAdd) {
            handleOperator("+");
        } else if (id == R.id.btnSubtract) {
            handleOperator("-");
        } else if (id == R.id.btnMultiply) {
            handleOperator("*");
        } else if (id == R.id.btnDivide) {
            handleOperator("/");
        } else if (id == R.id.btnPower) {
            handleOperator("^");
        } else if (id == R.id.btnEquals) {
            calculateResult();
        } else if (id == R.id.btnClear) {
            clear();
        } else if (id == R.id.btnBackspace) {
            backspace();
        } else if (id == R.id.btnPlusMinus) {
            toggleSign();
        } else if (id == R.id.btnPercent) {
            calculatePercent();
        } else if (id == R.id.btnSin) {
            calculateTrigonometric("sin");
        } else if (id == R.id.btnCos) {
            calculateTrigonometric("cos");
        } else if (id == R.id.btnTan) {
            calculateTrigonometric("tan");
        } else if (id == R.id.btnLog) {
            calculateLogarithm("log");
        } else if (id == R.id.btnLn) {
            calculateLogarithm("ln");
        } else if (id == R.id.btnSqrt) {
            calculateSquareRoot();
        } else if (id == R.id.btnSquare) {
            calculateSquare();
        } else if (id == R.id.btnFactorial) {
            calculateFactorial();
        } else if (id == R.id.btnInverse) {
            calculateInverse();
        } else if (id == R.id.btnAbs) {
            calculateAbsolute();
        } else if (id == R.id.btnPi) {
            insertConstant(Math.PI);
        } else if (id == R.id.btnE) {
            insertConstant(Math.E);
        } else if (id == R.id.btnMC) {
            memoryClear();
        } else if (id == R.id.btnMR) {
            memoryRecall();
        } else if (id == R.id.btnMPlus) {
            memoryAdd();
        } else if (id == R.id.btnOpenParen) {
            handleOperator("(");
        } else if (id == R.id.btnCloseParen) {
            handleOperator(")");
        }
    }

    private void handleNumberInput(String number) {
        if (isNewOperation) {
            currentNumber = number;
            isNewOperation = false;
        } else {
            if (currentNumber.equals("0")) {
                currentNumber = number;
            } else {
                currentNumber += number;
            }
        }
        updateDisplay();
    }

    private void handleDecimal() {
        if (isNewOperation) {
            currentNumber = "0.";
            isNewOperation = false;
            hasDecimal = true;
        } else if (!hasDecimal) {
            currentNumber += ".";
            hasDecimal = true;
        }
        updateDisplay();
    }

    private void handleOperator(String operator) {
        if (!previousOperation.isEmpty() && !isNewOperation) {
            calculateResult();
        }

        previousOperation += currentNumber + " " + operator + " ";
        tvPreviousOperation.setText(previousOperation);
        isNewOperation = true;
        hasDecimal = false;
    }

    private void calculateResult() {
        try {
            String expression = previousOperation + currentNumber;
            double result = evaluateExpression(expression);

            currentNumber = df.format(result);
            tvDisplay.setText(currentNumber);
            tvPreviousOperation.setText(expression + " =");

            previousOperation = "";
            isNewOperation = true;
            hasDecimal = currentNumber.contains(".");
        } catch (Exception e) {
            tvDisplay.setText("Error");
            clear();
        }
    }

    private double evaluateExpression(String expression) {
        // Remove spaces and replace display operators with standard ones
        expression = expression.replaceAll("\\s+", "")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-");

        return evaluate(expression);
    }

    private double evaluate(String expression) {
        Stack<Double> values = new Stack<>();
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < expression.length() &&
                        (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    sb.append(expression.charAt(i++));
                }
                values.push(Double.parseDouble(sb.toString()));
                i--;
            } else if (c == '(') {
                operators.push(c);
            } else if (c == ')') {
                while (operators.peek() != '(') {
                    values.push(applyOperation(operators.pop(), values.pop(), values.pop()));
                }
                operators.pop();
            } else if (isOperator(c)) {
                while (!operators.empty() && hasPrecedence(c, operators.peek())) {
                    values.push(applyOperation(operators.pop(), values.pop(), values.pop()));
                }
                operators.push(c);
            }
        }

        while (!operators.empty()) {
            values.push(applyOperation(operators.pop(), values.pop(), values.pop()));
        }

        return values.pop();
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        if ((op1 == '*' || op1 == '/' || op1 == '^') && (op2 == '+' || op2 == '-')) return false;
        if (op1 == '^' && (op2 == '*' || op2 == '/')) return false;
        return true;
    }

    private double applyOperation(char operator, double b, double a) {
        switch (operator) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new ArithmeticException("Division by zero");
                return a / b;
            case '^': return Math.pow(a, b);
            default: return 0;
        }
    }

    private void calculateTrigonometric(String function) {
        try {
            double value = Double.parseDouble(currentNumber);
            double radians = Math.toRadians(value);
            double result;

            switch (function) {
                case "sin": result = Math.sin(radians); break;
                case "cos": result = Math.cos(radians); break;
                case "tan": result = Math.tan(radians); break;
                default: return;
            }

            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText(function + "(" + value + ")");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void calculateLogarithm(String type) {
        try {
            double value = Double.parseDouble(currentNumber);
            if (value <= 0) throw new ArithmeticException("Invalid input for logarithm");

            double result = type.equals("log") ? Math.log10(value) : Math.log(value);
            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText(type + "(" + value + ")");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void calculateSquareRoot() {
        try {
            double value = Double.parseDouble(currentNumber);
            if (value < 0) throw new ArithmeticException("Invalid input for square root");

            double result = Math.sqrt(value);
            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText("√(" + value + ")");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void calculateSquare() {
        try {
            double value = Double.parseDouble(currentNumber);
            double result = value * value;
            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText("(" + value + ")²");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void calculateFactorial() {
        try {
            double value = Double.parseDouble(currentNumber);
            if (value < 0 || value != (int) value || value > 170) {
                throw new ArithmeticException("Invalid input for factorial");
            }

            double result = factorial((int) value);
            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText((int) value + "!");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private double factorial(int n) {
        if (n <= 1) return 1;
        double result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private void calculateInverse() {
        try {
            double value = Double.parseDouble(currentNumber);
            if (value == 0) throw new ArithmeticException("Division by zero");

            double result = 1.0 / value;
            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText("1/(" + value + ")");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void calculateAbsolute() {
        try {
            double value = Double.parseDouble(currentNumber);
            double result = Math.abs(value);
            currentNumber = df.format(result);
            updateDisplay();
            tvPreviousOperation.setText("|" + value + "|");
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void calculatePercent() {
        try {
            double value = Double.parseDouble(currentNumber);
            double result = value / 100.0;
            currentNumber = df.format(result);
            updateDisplay();
            isNewOperation = true;
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void insertConstant(double constant) {
        currentNumber = df.format(constant);
        updateDisplay();
        isNewOperation = true;
        hasDecimal = true;
    }

    private void toggleSign() {
        try {
            if (!currentNumber.equals("0")) {
                if (currentNumber.startsWith("-")) {
                    currentNumber = currentNumber.substring(1);
                } else {
                    currentNumber = "-" + currentNumber;
                }
                updateDisplay();
            }
        } catch (Exception e) {
            tvDisplay.setText("Error");
        }
    }

    private void backspace() {
        if (!isNewOperation && currentNumber.length() > 1) {
            if (currentNumber.endsWith(".")) {
                hasDecimal = false;
            }
            currentNumber = currentNumber.substring(0, currentNumber.length() - 1);
            updateDisplay();
        } else {
            currentNumber = "0";
            updateDisplay();
            isNewOperation = true;
        }
    }

    private void clear() {
        currentNumber = "0";
        previousOperation = "";
        isNewOperation = true;
        hasDecimal = false;
        updateDisplay();
        tvPreviousOperation.setText("");
    }

    private void memoryClear() {
        memory = 0;
    }

    private void memoryRecall() {
        currentNumber = df.format(memory);
        updateDisplay();
        isNewOperation = true;
        hasDecimal = currentNumber.contains(".");
    }

    private void memoryAdd() {
        try {
            memory += Double.parseDouble(currentNumber);
        } catch (Exception e) {
            // Handle error silently
        }
    }

    private void updateDisplay() {
        tvDisplay.setText(currentNumber);
    }
}