import 'package:flutter/material.dart';

class CalculatorScreen extends StatefulWidget {
  const CalculatorScreen({Key? key}) : super(key: key);

  @override
  State<CalculatorScreen> createState() => _CalculatorScreenState();
}

class _CalculatorScreenState extends State<CalculatorScreen> {
  String _display = "0";
  String _equation = "";
  double _firstOperand = 0;
  String _operator = "";
  bool _shouldClear = false;

  void _onButtonPressed(String text) {
    setState(() {
      if (text == "C") {
        _display = "0";
        _equation = "";
        _firstOperand = 0;
        _operator = "";
      } else if (text == "⌫") {
        if (_display.length > 1) {
          _display = _display.substring(0, _display.length - 1);
        } else {
          _display = "0";
        }
      } else if (["+", "-", "×", "÷"].contains(text)) {
        _firstOperand = double.tryParse(_display) ?? 0;
        _operator = text;
        _equation = "$_display $text";
        _shouldClear = true;
      } else if (text == "=") {
        if (_operator.isNotEmpty) {
          double secondOperand = double.tryParse(_display) ?? 0;
          double result = 0;
          if (_operator == "+") result = _firstOperand + secondOperand;
          if (_operator == "-") result = _firstOperand - secondOperand;
          if (_operator == "×") result = _firstOperand * secondOperand;
          if (_operator == "÷") result = secondOperand != 0 ? _firstOperand / secondOperand : 0;

          _equation = "$_equation $_display =";
          _display = result.toString().endsWith(".0") ? result.toInt().toString() : result.toString();
          _operator = "";
        }
      } else {
        if (_display == "0" || _shouldClear) {
          _display = text;
          _shouldClear = false;
        } else {
          _display += text;
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final buttons = [
      ["C", "⌫", "%", "÷"],
      ["7", "8", "9", "×"],
      ["4", "5", "6", "-"],
      ["1", "2", "3", "+"],
      ["0", ".", "π", "="],
    ];

    return Scaffold(
      backgroundColor: const Color(0xFF1E1E2C),
      appBar: AppBar(
        title: const Text('الآلة الحاسبة العلمية fx 🧮', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF27293D),
        foregroundColor: Colors.white,
      ),
      body: Column(
        children: [
          // Display
          Expanded(
            flex: 2,
            child: Container(
              padding: const EdgeInsets.all(20),
              alignment: Alignment.bottomRight,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(_equation, style: const TextStyle(color: Colors.white54, fontSize: 18)),
                  const SizedBox(height: 8),
                  Text(_display, style: const TextStyle(color: Colors.white, fontSize: 38, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
          ),
          const Divider(color: Colors.white24, height: 1),
          // Buttons
          Expanded(
            flex: 4,
            child: Container(
              padding: const EdgeInsets.all(12),
              child: Column(
                children: buttons.map((row) {
                  return Expanded(
                    child: Row(
                      children: row.map((btn) {
                        final isOp = ["+", "-", "×", "÷", "="].contains(btn);
                        final isAction = ["C", "⌫", "%"].contains(btn);
                        return Expanded(
                          child: Padding(
                            padding: const EdgeInsets.all(6.0),
                            child: ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: isOp
                                    ? const Color(0xFFFB8C00)
                                    : isAction
                                        ? const Color(0xFFE53935)
                                        : const Color(0xFF2D3047),
                                foregroundColor: Colors.white,
                                shape: RoundedCornerShape(14),
                              ),
                              onPressed: () => _onButtonPressed(btn),
                              child: Text(btn, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
