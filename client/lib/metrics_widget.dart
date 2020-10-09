import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          body: MetricsWidget(),
        ),
      ),
    );

class MetricsWidget extends StatefulWidget {
  @override
  _MetricsWidgetState createState() => _MetricsWidgetState();
}

class _MetricsWidgetState extends State<MetricsWidget> {
  Map<String, List<MetricEntry>> metrics = {};

  @override
  void initState() {
    super.initState();
    RestClient.metrics().then((value) => setState(() {
          metrics = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    List<Widget> children = [];
    metrics.forEach((key, value) {
      children.add(metricCard(key, value));
    });
    return SliverList(
      delegate: SliverChildListDelegate(children),
    );
  }

  Widget metricCard(String group, List<MetricEntry> metrics) {
    final List<Widget> kids = [];
    metrics.forEach((element) {
      kids.add(metricWidget(element));
    });
    return ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 350.0,
          maxHeight: 250.0,
        ),
        child: Card(
            clipBehavior: Clip.antiAlias,
            shadowColor: Colors.grey,
            elevation: 10,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.start,
              children: [
                ListTile(
                  leading: Icon(FontAwesomeIcons.asymmetrik),
                  title: Text(group),
                  subtitle: Text(
                    '${metrics.length} Metrics',
                    style: TextStyle(color: Colors.black.withOpacity(0.6)),
                  ),
                ),
                Wrap(spacing: 10, runSpacing: 10, children: kids)
              ],
            )));
  }

  Widget metricWidget(MetricEntry metric) {
    return Text(metric.metric.name);
  }
}
