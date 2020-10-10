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
    final colors = [
      Colors.yellow,
      Colors.green,
      Colors.blue,
      Colors.red,
      Colors.pink,
      Colors.deepPurple,
    ];
    return CustomScrollView(shrinkWrap: false, slivers: [
      SliverGrid(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            // mainAxisSpacing: 2.0,
            // crossAxisSpacing: 2.0,
            //childAspectRatio : 2,
            crossAxisCount: 1,
          ),
          delegate: SliverChildBuilderDelegate(
              (BuildContext context, int index) {
                return Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: new Container(
                      color: colors[index % colors.length],
                      height: 650.0,
                      child: children[index]),
                );
              },

            childCount: children.length))
    ]);
  }

  Widget metricCard(String group, List<MetricEntry> metrics) {
    final List<Widget> kids = [];
    metrics.forEach((element) {
      kids.add(metricWidget(element));
    });

    return CustomScrollView(shrinkWrap: true, slivers: [
      SliverGrid(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            mainAxisSpacing: 2.0,
            crossAxisSpacing: 2.0,
            //childAspectRatio : 2,
            crossAxisCount: 6,
          ),
          delegate: SliverChildBuilderDelegate(
                  (BuildContext context, int index) {
                return Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: new Container(
                      height: 650.0,
                      child: kids[index]),
                );
              },

              childCount: kids.length))
    ]);
  }
  Widget metricCard2(String group, List<MetricEntry> metrics) {
    final List<Widget> kids = [];
    metrics.forEach((element) {
      kids.add(metricWidget(element));
    });
    return ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 350.0,
          maxHeight: 6250.0,
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
    final tags = <Widget>[];
    metric.metric.tags.forEach((key, value) {
      final chip = Chip(
          avatar: CircleAvatar(
            backgroundColor: Colors.grey.shade800,
            child: Text('AB'),
          ),
        label : Text('${key} : ${value}')
      );
      tags.add(Padding(
        padding: const EdgeInsets.fromLTRB(8.0,0,8,0),
        child: chip,
      ));
    });
    return Card(
        clipBehavior: Clip.antiAlias,
        shadowColor: Colors.blue,
        elevation: 10,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            ListTile(
              leading: Icon(FontAwesomeIcons.asymmetrik),
              title: Text(metric.metric.name),
              subtitle: Text(
                metric.metric.description,
                style: TextStyle(color: Colors.black.withOpacity(0.6)),
              ),
            ),
            Row(children : tags
            ),
            Text(
              metric.value,
              style: TextStyle(color: Colors.green, fontSize: 18, fontWeight: FontWeight.bold),
            )
          ],
        ));
  }
}
