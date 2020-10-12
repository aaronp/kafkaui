import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';

import 'create_topic_dialog.dart';
import 'list_topics_widget.dart';
import 'navigation_drawer.dart';

class TopicsWidget extends StatefulWidget {
  static const path = '/list-topics';

  @override
  _TopicsWidgetState createState() => _TopicsWidgetState();
}

class _TopicsWidgetState extends State<TopicsWidget> {
  final GlobalKey<ScaffoldState> _scaffoldKey = new GlobalKey<ScaffoldState>();
  Key _listTopicsKey = ValueKey('xyz');

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: _scaffoldKey,
      appBar: AppBar(
        title: Text('Kafka'),
        actions: [
          CreateTopicDialog.createButton(context, _scaffoldKey),
          IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Refresh',
              onPressed: () => setState(() {
                    _listTopicsKey =
                        ValueKey('${DateTime.now().millisecondsSinceEpoch}');
                  }))
        ],
      ),
      drawer: NavigationDrawer(),
      body: Row(
        children: [
          ListTopicsWidget(key: _listTopicsKey),
        ],
      ),
    );
  }
}
