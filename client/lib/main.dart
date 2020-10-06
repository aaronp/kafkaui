import 'package:flutter/material.dart';
import 'package:kafkaui/config_widget.dart';

void main() {
  runApp(KafkaUIApp());
}

class KafkaUIApp extends StatelessWidget {
  static final appTheme = ThemeData(
    primarySwatch: Colors.orange,
    accentColor: Colors.blue,
    primaryColor: Colors.grey[700],
    primaryColorBrightness: Brightness.dark,
    visualDensity: VisualDensity.adaptivePlatformDensity,
  );

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        title: 'Kafka UI',
        initialRoute: ConfigWidget.path,
        theme: appTheme,
        routes: {
          ConfigWidget.path: (context) => ConfigWidget(title: 'Config')
        });
  }
}
